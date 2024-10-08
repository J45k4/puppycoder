use env::load_envs;
use generated::ToolCallParameters;
use generated::TOOLS;
use llm::*;
use types::*;
use ui::*;
use utility::get_projects_dir;
use std::collections::HashSet;
use std::fs::read_dir;
use std::io;
use std::net::TcpListener;
use wgui::*;

mod llm;
mod openai;
mod env;
mod history;
mod tool;
mod ui;
mod types;
mod generated;
mod utility;
mod autoupdate;

struct App {
	wgui: Wgui,
	clients: HashSet<usize>,
	state: State,
	llm_client: LLMClient,
}

impl App {
	pub fn new(projects: Vec<Project>, port: u16) -> App {
		let state = State {
			projects,
			max_conversation_turns: 5,
			max_context_size: 20,
			..Default::default()
		};

		App {
			wgui: Wgui::new(format!("127.0.0.1:{}", port).parse().unwrap()),
			clients: HashSet::new(),
			state,
			llm_client: LLMClient::new(),
		}
	}

	async fn render_ui(&mut self) {
		let item = ui(&self.state);

		for client_id in &self.clients {
			self.wgui.render(*client_id, item.clone()).await;
		}
	}

	fn send_message(&mut self) {
		self.state.conversation_turns = 0;
		let current_msg = self.state.current_msg.clone();
		self.state.current_msg.clear();
		let project = match self.get_active_project() {
			Some(project) => project,
			None => return,
		};
		if !current_msg.is_empty() {
			project.history.add_message(LLMMessage::User(current_msg));
			project.modified = true;
		}
		self.continue_conversation();
	}

	fn continue_conversation(&mut self) {
		let max_context_size = self.state.max_context_size;
		let project = match self.get_active_project() {
			Some(project) => project,
			None => return,
		};
		let mut messages = Vec::new();
		let mut assistant_msg = String::new();
		assistant_msg += r"You are puppycoder assistant 🐶\n 
You are good at programming and will help users complete their programming tasks.
You can use tools provided to you to read and write files.";

		if !project.instructions.is_empty() {
			assistant_msg += &format!("Instructions: {}\n", project.instructions);
		}
		if !project.name.is_empty() {
			assistant_msg += &format!("Project name: {}\n", project.name);
		}

		if !project.instructions.is_empty() {
			let msg = LLMMessage::System(project.instructions.clone());
			messages.push(msg);
		}
		for _ in 0..max_context_size {
			if let Some(item) = project.history.items.last() {
				messages.push(item.content.clone());
			} else {
				break;
			}
		}
		let req = GenRequest {
			model: project.model.clone(),
			messages,
			tools: TOOLS.iter()
				.filter(|tool| project.activated_tools.contains(tool))
				.cloned().collect(),
		};

		self.llm_client.gen(req);
	}

	fn get_active_project(&mut self) -> Option<&mut Project> {
		let active_project = match self.state.active_project {
			Some(inx) => inx,
			None => return None,
		};

		self.state.projects.get_mut(active_project)
	}

	async fn handle_event(&mut self, event: ClientEvent) {
		match event {
			ClientEvent::Disconnected { id } => {
				self.clients.remove(&id);
			}
			ClientEvent::Connected { id } => {
				self.clients.insert(id);
			}
			ClientEvent::OnClick(o) => match o.id {
				SELECT_PROJECT_LINK => {
					self.state.active_project = Some(o.inx.unwrap() as usize);
				}
				SEND_MESSAGE_BUTTON => {
					log::info!("Send message button clicked");
					self.send_message();
				}
				TOOL_CHECKBOX => {
					if let Some(project) = self.get_active_project() {
						let inx = o.inx.unwrap() as usize;
						match project.activated_tools.iter().position(|tool| tool == &TOOLS[inx]) {
							Some(i) => {
								project.activated_tools.remove(i);
							}
							None => {
								project.activated_tools.push(TOOLS[inx].clone());
							}
						}
					}
				}
				SELECT_PROJECT_FOLDER => {
					match rfd::AsyncFileDialog::new().pick_folder().await {
						Some(handle) => {
							if let Some(project) = self.get_active_project() {
								project.modified = true;
								project.folder_path = handle.path().to_string_lossy().to_string();
							}
						},
						None => {
							log::info!("No folder selected");
						}
					}
				}
				NEW_PROJECT_BUTTON => {
					let project = Project {
						modified: true,
						..Default::default()
					};
					self.state.projects.push(project);
				}
				SAVE_PRJECT_BUTTON => {
					if let Some(project) = self.get_active_project() {
						project.modified = false;
						let save_path = get_projects_dir().join(format!("{}.json", project.name));
						let content = serde_json::to_string_pretty(project).unwrap();
						tokio::fs::write(save_path, content).await.unwrap();
					}
				}
				NEW_FORBIDDEN_FILE_BUTTON => {
					let new_forbidden_file_name = self.state.new_forbidden_file_name.clone();
					if let Some(project) = self.get_active_project() {
						project.forbidden_files.push(new_forbidden_file_name);
						project.modified = true;
					}
				}
				DELETE_FORBIDDEN_FILE_BUTTON => {
					if let Some(project) = self.get_active_project() {
						project.forbidden_files.remove(o.inx.unwrap() as usize);
						project.modified = true;
					}
				}
				_ => {}
			},
			ClientEvent::OnTextChanged(t) => match t.id {
				MESSAGE_INPUT => {
					self.state.current_msg = t.value;
				}
				PROJECT_NAME_INPUT => {
					if let Some(project) = self.get_active_project() {
						project.name = t.value;
						project.modified = true;
					}
				}
				INSTRUCTIONS_TEXT_INPUT => {
					if let Some(project) = self.get_active_project() {
						project.instructions = t.value;
						project.modified = true;
					}
				}
				NEW_FORBIDDEN_FILE_NAME => {
					self.state.new_forbidden_file_name = t.value;
				}
				MAX_CONVERSATION_TURNS => {
					if let Ok(t) = t.value.parse::<u32>() {
						self.state.max_conversation_turns = t;
					}
				}
				MAX_CONTEXT_SIZE => {
					if let Ok(t) = t.value.parse::<u32>() {
						self.state.max_context_size = t;
					}
				}
				_ => {}
			},
			ClientEvent::OnSelect(event) => {
				match event.id {
					MODEL_SELECT => {
						log::info!("model selected: {:?}", event.value);
						if let Some(project) = self.get_active_project() {
							project.model = LLMModel::from_str(&event.value).unwrap();
							project.modified = true;
						}
					}
					_ => {}
				}
			}
			_ => {}
		};

		self.render_ui().await;
	}

	async fn handle_result(&mut self, result: GenResult) {
		match result {
			GenResult::Response(mut res) => {
				log::info!("Response: {:?}", res);
				if let Some(project) = self.get_active_project() {
					let should_continue = res.msg.tool_calls.len() > 0;
	
					for tool_call in res.msg.tool_calls.iter_mut() {
						let should_exec = match &tool_call.tool {
							ToolCallParameters::ExecuteBashCmd(args) => {
								
								true
							},
							_ => false
						};
						if should_exec {
							match tool::execute(&project, &tool_call.tool).await {
								Ok(res) => {
									log::info!("tool call result: {:?}", res);
									project.history.add_message(LLMMessage::ToolResponse(ToolResponse { 
										id: tool_call.id.clone(), 
										content: res
									}))
								}
								Err(e) => {
									log::info!("tool call error: {:?}", e);
									project.history.add_message(LLMMessage::ToolResponse(ToolResponse { 
										id: tool_call.id.clone(), 
										content: e.to_string()
									}))
								}
							}
						} else {
							tool_call.waiting_permission = true;
						}
					}

					project.history.add_message(LLMMessage::Assistant(res.msg.clone()));
					project.input_token_count += res.prompt_tokens;
					project.output_token_count += res.completion_tokens;
					project.input_token_cost += res.promt_cost;
					project.output_token_cost += res.completion_cost;
					project.modified = true;

					if should_continue {
						if self.state.conversation_turns < self.state.max_conversation_turns {
							self.continue_conversation();
						}
						self.state.conversation_turns += 1;
					}
				}
			},
			GenResult::Error(e) => {
				log::info!("Error: {:?}", e);
			},
		}
	}

	async fn run(mut self) {
		loop {
			tokio::select! {
				event = self.wgui.next() => {
					match event {
						Some(e) => {
							log::info!("Event: {:?}", e);
							self.handle_event(e).await;
						},
						None => {
							log::info!("No event");
							break;
						},
					}
				}
				result = self.llm_client.next() => {
					match result {
						Some(res) => {
							log::info!("Result: {:?}", res);
							self.handle_result(res).await;
						},
						None => {
							log::info!("No result");
							break;
						},
					}
				}
			}
			self.render_ui().await;
		}
	}
}

fn find_first_free_port(start_port: u16, end_port: u16) -> Option<u16> {
    for port in start_port..=end_port {
        match TcpListener::bind(("127.0.0.1", port)) {
            Ok(listener) => {
                // Successfully bound to the port, so it's free.
                // Drop the listener to release the port.
                drop(listener);
                return Some(port);
            }
            Err(e) => {
                if e.kind() == io::ErrorKind::AddrInUse {
                    // Port is in use; try the next one.
                    continue;
                } else {
                    // Handle other errors (e.g., permission denied).
                    eprintln!("Failed to bind to port {}: {}", port, e);
                    continue;
                }
            }
        }
    }
    // No free port found in the specified range.
    None
}

#[tokio::main]
async fn main() {
	simple_logger::init_with_level(log::Level::Info).unwrap();
	load_envs();
	let projects_path = get_projects_dir();
	let conent = read_dir(projects_path).unwrap();
	let projects: Vec<Project> = conent
		.filter_map(|entry| {
			let entry = entry.unwrap();
			let path = entry.path();
			if path.is_file() {
				log::info!("Loading project: {:?}", path);
				let content = std::fs::read_to_string(path).unwrap();
				Some(serde_json::from_str(&content).unwrap())
			} else {
				None
			}
		})
		.collect();
	let port = find_first_free_port(7760, 7780).unwrap();
	App::new(projects, port).run().await;
}
