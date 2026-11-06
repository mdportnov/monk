use miette::Diagnostic;
use thiserror::Error;

pub type Result<T, E = Error> = std::result::Result<T, E>;

#[derive(Debug, Error, Diagnostic)]
pub enum Error {
    #[error("io error: {0}")]
    #[diagnostic(code(monk::io))]
    Io(#[from] std::io::Error),

    #[error("config error: {0}")]
    #[diagnostic(code(monk::config))]
    Config(String),

    #[error("invalid config file")]
    #[diagnostic(code(monk::config::parse))]
    ConfigParse(#[from] toml::de::Error),

    #[error("storage error: {0}")]
    #[diagnostic(code(monk::storage))]
    Storage(#[from] rusqlite::Error),

    #[error("the monk background service is not running")]
    #[diagnostic(
        code(monk::daemon::not_running),
        help(
            "start it with `{}`, or let `monk doctor --fix` find what is wrong",
            crate::daemon::install_hint()
        )
    )]
    DaemonNotRunning,

    #[error("the background service is already running (pid {0})")]
    #[diagnostic(code(monk::daemon::already_running))]
    DaemonAlreadyRunning(u32),

    #[error("this is a no-exit session: it cannot be stopped early")]
    #[diagnostic(
        code(monk::hard_mode::active),
        help("the only way out is the delayed emergency exit: `monk panic`")
    )]
    HardModeActive,

    #[error("permission denied: {0}")]
    #[diagnostic(
        code(monk::permission),
        help("monk needs elevated privileges to modify the hosts file")
    )]
    Permission(String),

    #[error("site blocking unavailable: {0}")]
    #[diagnostic(
        code(monk::blocker::unavailable),
        help(
            "install the background service so it can manage /etc/hosts: `{}`",
            crate::daemon::install_hint()
        )
    )]
    BlockerUnavailable(String),

    #[error("could not talk to the background service: {0}")]
    #[diagnostic(code(monk::ipc))]
    Ipc(String),

    #[error("session not found: {0}")]
    #[diagnostic(code(monk::session::not_found))]
    SessionNotFound(String),

    #[error("invalid duration: {0}")]
    #[diagnostic(code(monk::duration))]
    Duration(#[from] humantime::DurationError),

    #[error("elevation cancelled or failed: {0}")]
    #[diagnostic(code(monk::elevation), help("run `sudo monk service install` from a terminal"))]
    Elevation(String),

    #[error("{0}")]
    #[diagnostic(code(monk::limits))]
    Limit(String),

    #[error("the background service answered in a way this monk does not understand")]
    #[diagnostic(
        code(monk::ipc::unexpected),
        help(
            "the service and the cli are probably different versions — refresh it with `{}`",
            crate::daemon::install_hint()
        )
    )]
    UnexpectedResponse,

    #[error("{0}")]
    Other(String),
}

impl From<serde_json::Error> for Error {
    fn from(e: serde_json::Error) -> Self {
        Self::Ipc(e.to_string())
    }
}
