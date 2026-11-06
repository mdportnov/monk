//! One scale for "how hard is it to leave a session early", shown in the
//! editor and on the start screen instead of a pile of raw limits.
//!
//! The scale is a view over existing settings, nothing is stored under its
//! name: a routine with a cooldown is "delayed exit", without one it is
//! "soft", and "no-exit" is the hard flag chosen when a session starts.

use std::time::Duration;

use ratatui::{
    style::{Color, Modifier, Style},
    text::Span,
};

use crate::config::Limits;
use crate::tui::theme::*;

/// The cooldown a routine gets when it is switched to "delayed exit" and had
/// none of its own.
pub const DEFAULT_COOLDOWN: Duration = Duration::from_secs(10 * 60);

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Firmness {
    Soft,
    DelayedExit,
    NoExit,
}

impl Firmness {
    pub const ALL: [Firmness; 3] = [Firmness::Soft, Firmness::DelayedExit, Firmness::NoExit];

    /// The level a routine sits at by its own settings, before the start
    /// screen decides whether this particular session is no-exit.
    pub fn of_limits(limits: &Limits) -> Self {
        if limits.cooldown.is_some() {
            Firmness::DelayedExit
        } else {
            Firmness::Soft
        }
    }

    pub fn of_session(limits: &Limits, hard: bool) -> Self {
        if hard {
            Firmness::NoExit
        } else {
            Self::of_limits(limits)
        }
    }

    pub fn label(self) -> String {
        match self {
            Firmness::Soft => crate::i18n::t!("tui.firmness.soft").to_string(),
            Firmness::DelayedExit => crate::i18n::t!("tui.firmness.delayed").to_string(),
            Firmness::NoExit => crate::i18n::t!("tui.firmness.no_exit").to_string(),
        }
    }

    /// One sentence of what this level means for the person in the session.
    pub fn note(self, limits: &Limits) -> String {
        match self {
            Firmness::Soft => crate::i18n::t!("tui.firmness.soft_note").to_string(),
            Firmness::DelayedExit => {
                let cd = crate::tui::view::fmt_short(limits.cooldown.unwrap_or(DEFAULT_COOLDOWN));
                crate::i18n::t!("tui.firmness.delayed_note", cooldown = cd).to_string()
            }
            Firmness::NoExit => crate::i18n::t!("tui.firmness.no_exit_note").to_string(),
        }
    }

    pub fn color(self) -> Color {
        match self {
            Firmness::Soft => ACCENT,
            Firmness::DelayedExit => GLOW,
            Firmness::NoExit => ALERT,
        }
    }
}

/// `soft · delayed exit · no-exit` with the current level lit. Levels not in
/// `reachable` are drawn dimmer: the editor cannot store "no-exit".
pub fn scale_spans(current: Firmness, reachable: &[Firmness], focused: bool) -> Vec<Span<'static>> {
    let mut spans = Vec::new();
    for (i, level) in Firmness::ALL.iter().enumerate() {
        if i > 0 {
            spans.push(Span::styled("  ", Style::default().fg(DIM)));
        }
        let label = format!(" {} ", level.label());
        let style = if *level == current {
            let s =
                Style::default().fg(Color::Black).bg(level.color()).add_modifier(Modifier::BOLD);
            if focused {
                s
            } else {
                s.add_modifier(Modifier::DIM)
            }
        } else if reachable.contains(level) {
            Style::default().fg(TEXT)
        } else {
            Style::default().fg(DIM)
        };
        spans.push(Span::styled(label, style));
    }
    spans
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn cooldown_decides_the_base_level() {
        let mut l = Limits::default();
        assert_eq!(Firmness::of_limits(&l), Firmness::Soft);
        l.cooldown = Some(Duration::from_secs(60));
        assert_eq!(Firmness::of_limits(&l), Firmness::DelayedExit);
        assert_eq!(Firmness::of_session(&l, true), Firmness::NoExit);
    }
}
