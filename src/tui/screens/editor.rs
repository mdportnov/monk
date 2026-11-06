use crossterm::event::{KeyCode, KeyEvent, KeyModifiers};
use ratatui::{
    layout::{Alignment, Constraint, Direction, Layout, Rect},
    style::{Color, Modifier, Style},
    text::{Line, Span},
    widgets::{Block, Borders, Paragraph, Wrap},
    Frame,
};
use std::time::Duration;

use crate::{
    config::{Hooks, Limits, Profile},
    tui::{
        app::{App, EditorField, Screen},
        firmness::{scale_spans, Firmness, DEFAULT_COOLDOWN},
        theme::*,
        view::{draw_header, fmt_schedule, picker_block},
        widgets::{MultiSelectItem, MultiSelectList, TextInput},
    },
};

/// Prefixes that tell the four kinds of blocklist entries apart inside the
/// single list. They never reach the config: `build_profile` strips them
/// back into `brands`, `site_groups`, `sites` and `apps`.
const BRAND: &str = "brand:";
const GROUP: &str = "group:";
const SITE: &str = "site:";
const APP: &str = "app:";

/// What the "+ add" modal is adding. Tab switches between the two.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CustomKind {
    Site,
    App,
}

#[derive(Debug)]
pub struct EditorState {
    pub original_name: Option<String>,
    pub name: TextInput,
    pub color_idx: usize,
    pub max: TextInput,
    pub min: TextInput,
    pub cooldown: TextInput,
    pub panic_delay: TextInput,
    pub hook_before: TextInput,
    pub hook_after: TextInput,
    pub blocklist: MultiSelectList,
    pub focus: EditorField,
    pub advanced_open: bool,
    pub snapshot: Profile,
    pub error: Option<String>,
    /// The field that triggered `error`, if any. Used to draw a red label.
    pub error_field: Option<EditorField>,
    pub confirm_cancel: bool,
    /// When Some, the inline "add your own site or app" modal is open.
    /// Enter commits, Esc discards, Tab switches between site and app.
    pub add_custom: Option<(CustomKind, TextInput)>,
    /// Inline error from the add-custom modal (e.g. invalid hostname).
    pub add_custom_error: Option<String>,
}

pub const COLOR_PALETTE: &[(&str, &str)] = &[
    ("none", "—"),
    ("blue", "blue"),
    ("cyan", "cyan"),
    ("green", "green"),
    ("amber", "amber"),
    ("violet", "violet"),
    ("red", "red"),
];

pub fn palette_index(color: &Option<String>) -> usize {
    let key = color.as_deref().unwrap_or("none");
    COLOR_PALETTE.iter().position(|(k, _)| *k == key).unwrap_or(0)
}

pub fn palette_value(idx: usize) -> Option<String> {
    let (k, _) = COLOR_PALETTE[idx % COLOR_PALETTE.len()];
    if k == "none" {
        None
    } else {
        Some(k.to_string())
    }
}

impl EditorState {
    pub fn new_mode() -> Self {
        Self::build(None, Profile::default())
    }

    pub fn edit(name: String, profile: Profile) -> Self {
        Self::build(Some(name), profile)
    }

    fn build(name: Option<String>, profile: Profile) -> Self {
        let limits = profile.limits.clone();
        let mut s = Self {
            name: TextInput::new(name.clone().unwrap_or_default()),
            original_name: name,
            color_idx: palette_index(&profile.color),
            max: TextInput::new(fmt_opt_humantime(limits.max_duration)),
            min: TextInput::new(fmt_opt_humantime(limits.min_duration)),
            cooldown: TextInput::new(fmt_opt_humantime(limits.cooldown)),
            panic_delay: TextInput::new(fmt_opt_humantime(limits.panic_delay)),
            hook_before: TextInput::new(profile.hooks.before.join(" && ")),
            hook_after: TextInput::new(profile.hooks.after.join(" && ")),
            blocklist: load_blocklist(&profile),
            focus: EditorField::Name,
            // A routine that already uses a raw limit or a hook opens with
            // them visible, so nothing it does is hidden from its owner.
            advanced_open: limits.max_duration.is_some()
                || limits.min_duration.is_some()
                || limits.panic_delay.is_some()
                || !profile.hooks.before.is_empty()
                || !profile.hooks.after.is_empty(),
            snapshot: profile,
            error: None,
            error_field: None,
            confirm_cancel: false,
            add_custom: None,
            add_custom_error: None,
        };
        s.sync_focus();
        s
    }

    pub fn input_mut(&mut self, f: EditorField) -> Option<&mut TextInput> {
        match f {
            EditorField::Name => Some(&mut self.name),
            EditorField::Max => Some(&mut self.max),
            EditorField::Min => Some(&mut self.min),
            EditorField::Cooldown => Some(&mut self.cooldown),
            EditorField::PanicDelay => Some(&mut self.panic_delay),
            EditorField::HookBefore => Some(&mut self.hook_before),
            EditorField::HookAfter => Some(&mut self.hook_after),
            _ => None,
        }
    }

    fn input(&self, f: EditorField) -> Option<&TextInput> {
        match f {
            EditorField::Name => Some(&self.name),
            EditorField::Max => Some(&self.max),
            EditorField::Min => Some(&self.min),
            EditorField::Cooldown => Some(&self.cooldown),
            EditorField::PanicDelay => Some(&self.panic_delay),
            EditorField::HookBefore => Some(&self.hook_before),
            EditorField::HookAfter => Some(&self.hook_after),
            _ => None,
        }
    }

    pub fn visible_fields(&self) -> Vec<EditorField> {
        EditorField::ORDER.into_iter().filter(|f| self.advanced_open || !f.is_advanced()).collect()
    }

    pub fn next_field(&mut self) {
        self.blocklist.filter_clear();
        let fields = self.visible_fields();
        let idx = fields.iter().position(|f| *f == self.focus).unwrap_or(0);
        self.focus = fields[(idx + 1) % fields.len()];
        self.sync_focus();
    }

    pub fn prev_field(&mut self) {
        self.blocklist.filter_clear();
        let fields = self.visible_fields();
        let idx = fields.iter().position(|f| *f == self.focus).unwrap_or(0);
        self.focus = fields[(idx + fields.len() - 1) % fields.len()];
        self.sync_focus();
    }

    pub fn toggle_advanced(&mut self) {
        self.advanced_open = !self.advanced_open;
        if !self.advanced_open && self.focus.is_advanced() {
            self.focus = EditorField::Advanced;
        }
        self.sync_focus();
    }

    /// The firmness the editor can store. "No-exit" is a property of one
    /// session, picked on the start screen, so it is never stored here.
    pub fn firmness(&self) -> Firmness {
        if self.cooldown.value.trim().is_empty() {
            Firmness::Soft
        } else {
            Firmness::DelayedExit
        }
    }

    pub fn set_firmness(&mut self, level: Firmness) {
        match level {
            Firmness::Soft => self.cooldown.set(""),
            Firmness::DelayedExit | Firmness::NoExit => {
                if self.cooldown.value.trim().is_empty() {
                    let fallback = self.snapshot.limits.cooldown.unwrap_or(DEFAULT_COOLDOWN);
                    self.cooldown.set(humantime::format_duration(fallback).to_string());
                }
            }
        }
    }

    fn sync_focus(&mut self) {
        let focus = self.focus;
        for f in EditorField::ORDER {
            let is = f == focus;
            if let Some(inp) = self.input_mut(f) {
                inp.focused = is;
            }
        }
    }

    pub fn build_profile(
        &self,
    ) -> std::result::Result<(String, Profile), (Option<EditorField>, String)> {
        let name = self.name.value.trim().to_string();
        if name.is_empty() {
            return Err((
                Some(EditorField::Name),
                crate::i18n::t!("tui.editor.name_required").to_string(),
            ));
        }
        if name.chars().count() > 30 {
            return Err((
                Some(EditorField::Name),
                crate::i18n::t!("tui.editor.name_too_long").to_string(),
            ));
        }
        let max_duration =
            parse_opt_humantime(&self.max).map_err(|e| (Some(EditorField::Max), e))?;
        let min_duration =
            parse_opt_humantime(&self.min).map_err(|e| (Some(EditorField::Min), e))?;
        let cooldown =
            parse_opt_humantime(&self.cooldown).map_err(|e| (Some(EditorField::Cooldown), e))?;
        let panic_delay = parse_opt_humantime(&self.panic_delay)
            .map_err(|e| (Some(EditorField::PanicDelay), e))?;
        let limits = Limits {
            max_duration,
            min_duration,
            cooldown,
            // Not enforced any more and not shown; kept so saving the routine
            // does not silently rewrite a value its owner put in the file.
            daily_cap: self.snapshot.limits.daily_cap,
            panic_delay,
        };
        if let (Some(mn), Some(mx)) = (limits.min_duration, limits.max_duration) {
            if mn > mx {
                return Err((
                    Some(EditorField::Min),
                    crate::i18n::t!("tui.editor.min_above_max").to_string(),
                ));
            }
        }
        let hooks = Hooks {
            before: split_cmds(&self.hook_before.value),
            after: split_cmds(&self.hook_after.value),
        };
        let mut brands = Vec::new();
        let mut site_groups = Vec::new();
        let mut sites = Vec::new();
        let mut apps = Vec::new();
        for id in self.blocklist.selected_ids() {
            if let Some(v) = id.strip_prefix(BRAND) {
                brands.push(v.to_string());
            } else if let Some(v) = id.strip_prefix(GROUP) {
                site_groups.push(v.to_string());
            } else if let Some(v) = id.strip_prefix(SITE) {
                sites.push(v.to_string());
            } else if let Some(v) = id.strip_prefix(APP) {
                apps.push(v.to_string());
            }
        }
        let profile = Profile {
            sites,
            site_groups,
            brands,
            apps,
            allow: self.snapshot.allow.clone(),
            hooks,
            limits,
            color: palette_value(self.color_idx),
            schedule: self.snapshot.schedule.clone(),
        };
        Ok((name, profile))
    }

    pub fn is_dirty(&self) -> bool {
        match self.build_profile() {
            Ok((name, p)) => {
                if self.original_name.is_none() {
                    return true;
                }
                if self.original_name.as_deref() != Some(name.as_str()) {
                    return true;
                }
                !profile_eq(&p, &self.snapshot)
            }
            Err(_) => true,
        }
    }

    fn add_custom_commit(&mut self) {
        let Some((kind, input)) = &self.add_custom else { return };
        let raw = input.value.trim().to_string();
        match kind {
            CustomKind::App => {
                if raw.is_empty() {
                    self.add_custom_error =
                        Some(crate::i18n::t!("tui.editor.bundle_id_required").to_string());
                } else if !looks_like_bundle_id(&raw) {
                    self.add_custom_error =
                        Some(crate::i18n::t!("tui.editor.bundle_id_format").to_string());
                } else {
                    self.blocklist.add_item_selected(app_item(&raw, None));
                    self.add_custom = None;
                    self.add_custom_error = None;
                }
            }
            CustomKind::Site => match crate::sites::sanitize_host(&raw) {
                Some(host) => {
                    self.blocklist.add_item_selected(site_item(&host));
                    self.add_custom = None;
                    self.add_custom_error = None;
                }
                None => {
                    self.add_custom_error =
                        Some(crate::i18n::t!("tui.editor.hostname_format").to_string());
                }
            },
        }
    }
}

pub async fn handle_editor_key(app: &mut App, key: KeyEvent) {
    let Screen::ModeEditor(ed) = &mut app.screen else { return };

    if let Some((kind, input)) = &mut ed.add_custom {
        match key.code {
            KeyCode::Esc => {
                ed.add_custom = None;
                ed.add_custom_error = None;
            }
            KeyCode::Tab | KeyCode::BackTab => {
                *kind = match kind {
                    CustomKind::Site => CustomKind::App,
                    CustomKind::App => CustomKind::Site,
                };
                ed.add_custom_error = None;
            }
            KeyCode::Enter => ed.add_custom_commit(),
            _ => {
                input.handle(key);
                ed.add_custom_error = None;
            }
        }
        return;
    }

    if ed.confirm_cancel {
        match key.code {
            KeyCode::Char('y') | KeyCode::Char('Y') => {
                app.open_picker();
            }
            KeyCode::Esc | KeyCode::Char('n') | KeyCode::Char('N') => {
                ed.confirm_cancel = false;
            }
            _ => {}
        }
        return;
    }

    // `+` is not part of any hostname or bundle id, so taking it before the
    // list's type-to-filter sees it is safe.
    if matches!(key.code, KeyCode::Char('+')) && ed.focus == EditorField::Blocklist {
        let mut input = TextInput::new("");
        input.focused = true;
        ed.add_custom = Some((CustomKind::Site, input));
        ed.add_custom_error = None;
        return;
    }
    if key.modifiers.contains(KeyModifiers::CONTROL) {
        match key.code {
            KeyCode::Char('s') => {
                app.save_editor().await;
                return;
            }
            KeyCode::Enter => {
                app.save_editor_and_open_confirm().await;
                return;
            }
            _ => {}
        }
    }
    // ↑/↓ move between fields everywhere except the blocklist, which uses
    // them for its own rows.
    let focus = ed.focus;
    if focus != EditorField::Blocklist {
        match key.code {
            KeyCode::Up => {
                ed.prev_field();
                return;
            }
            KeyCode::Down => {
                ed.next_field();
                return;
            }
            _ => {}
        }
    }
    match key.code {
        KeyCode::Esc => {
            let consumed = focus == EditorField::Blocklist && ed.blocklist.handle(key);
            if !consumed {
                if ed.is_dirty() {
                    ed.confirm_cancel = true;
                } else {
                    app.open_picker();
                }
            }
        }
        KeyCode::Tab => ed.next_field(),
        KeyCode::BackTab => ed.prev_field(),
        _ => {
            match focus {
                EditorField::Blocklist => {
                    ed.blocklist.handle(key);
                }
                EditorField::Color => {
                    let n = COLOR_PALETTE.len();
                    match key.code {
                        KeyCode::Left | KeyCode::Char('h') => {
                            ed.color_idx = (ed.color_idx + n - 1) % n;
                        }
                        KeyCode::Right | KeyCode::Char('l') => {
                            ed.color_idx = (ed.color_idx + 1) % n;
                        }
                        _ => {}
                    }
                }
                EditorField::Firmness => match key.code {
                    KeyCode::Left | KeyCode::Char('h') => ed.set_firmness(Firmness::Soft),
                    KeyCode::Right | KeyCode::Char('l') => ed.set_firmness(Firmness::DelayedExit),
                    KeyCode::Char(' ') => {
                        let next = match ed.firmness() {
                            Firmness::Soft => Firmness::DelayedExit,
                            _ => Firmness::Soft,
                        };
                        ed.set_firmness(next);
                    }
                    _ => {}
                },
                EditorField::Advanced => {
                    if matches!(key.code, KeyCode::Enter | KeyCode::Char(' ')) {
                        ed.toggle_advanced();
                    }
                }
                EditorField::Schedule => {
                    if matches!(key.code, KeyCode::Enter) {
                        open_schedule(app);
                        return;
                    }
                }
                _ => {
                    if let Some(input) = ed.input_mut(focus) {
                        input.handle(key);
                    }
                }
            }
            if let Screen::ModeEditor(ed) = &mut app.screen {
                ed.error = None;
                ed.error_field = None;
            }
        }
    }
}

/// The schedule has one editor: the form on the Schedules screen. Jumping
/// there with unsaved edits would let the form save a profile that a later
/// ctrl+s here overwrites, so the jump waits for a save.
fn open_schedule(app: &mut App) {
    let Screen::ModeEditor(ed) = &mut app.screen else { return };
    let Some(name) = ed.original_name.clone() else {
        ed.error = Some(crate::i18n::t!("tui.editor.schedule_save_first_new").to_string());
        ed.error_field = Some(EditorField::Schedule);
        return;
    };
    if ed.is_dirty() {
        ed.error = Some(crate::i18n::t!("tui.editor.schedule_save_first").to_string());
        ed.error_field = Some(EditorField::Schedule);
        return;
    }
    app.open_schedule_form(&name);
}

pub fn draw_editor(f: &mut Frame, app: &App, editor: &EditorState) {
    let area = f.area();
    let editing_running = matches!(
        (&editor.original_name, &app.globals.active),
        (Some(name), Some(s)) if &s.profile == name
    );
    let banner_h: u16 = if editing_running { 1 } else { 0 };
    let outer = Layout::default()
        .direction(Direction::Vertical)
        .constraints([
            Constraint::Length(3),
            Constraint::Length(banner_h),
            Constraint::Min(10),
            Constraint::Length(3),
        ])
        .split(area);

    draw_header(f, outer[0], app);

    if editing_running {
        let banner = Paragraph::new(Span::styled(
            crate::i18n::t!("tui.editor.live_session_drift").to_string(),
            Style::default().fg(Color::Black).bg(GLOW).add_modifier(Modifier::BOLD),
        ))
        .alignment(Alignment::Center);
        f.render_widget(banner, outer[1]);
    }

    let body = Layout::default()
        .direction(Direction::Horizontal)
        .constraints([Constraint::Percentage(55), Constraint::Percentage(45)])
        .split(outer[2]);

    draw_editor_fields(f, body[0], editor);
    draw_blocklist(f, body[1], editor);

    let help = crate::i18n::t!("tui.editor.footer").to_string();
    let footer = Paragraph::new(Span::styled(help, Style::default().fg(DIM)))
        .alignment(Alignment::Center)
        .block(Block::default().borders(Borders::TOP).border_style(Style::default().fg(DIM)));
    f.render_widget(footer, outer[3]);

    if editor.confirm_cancel {
        crate::tui::view::draw_confirm_modal(
            f,
            &crate::i18n::t!("tui.editor.discard_modal.title"),
            vec![
                Line::from(""),
                Line::from(Span::styled(
                    crate::i18n::t!("tui.editor.discard_modal.unsaved"),
                    Style::default().fg(TEXT),
                )),
                Line::from(""),
                Line::from(Span::styled(
                    crate::i18n::t!("tui.editor.discard_modal.question"),
                    Style::default().fg(DIM),
                )),
                Line::from(""),
            ],
            &crate::i18n::t!("tui.editor.discard_modal.confirm"),
            &crate::i18n::t!("tui.editor.discard_modal.cancel"),
            true,
        );
    }

    if let Some((kind, input)) = &editor.add_custom {
        draw_add_custom_modal(f, *kind, input, editor.add_custom_error.as_deref());
    }
}

fn draw_add_custom_modal(f: &mut Frame, kind: CustomKind, input: &TextInput, error: Option<&str>) {
    let area = f.area();
    let width = 60.min(area.width.saturating_sub(4));
    let height = 11u16.min(area.height.saturating_sub(4));
    let x = area.x + (area.width.saturating_sub(width)) / 2;
    let y = area.y + (area.height.saturating_sub(height)) / 2;
    let rect = Rect { x, y, width, height };

    f.render_widget(ratatui::widgets::Clear, rect);

    let block = Block::default()
        .borders(Borders::ALL)
        .border_type(ratatui::widgets::BorderType::Rounded)
        .border_style(Style::default().fg(ACCENT))
        .title(Span::styled(
            crate::i18n::t!("tui.editor.add_title").to_string(),
            Style::default().fg(ACCENT).add_modifier(Modifier::BOLD),
        ));
    let inner = block.inner(rect);
    f.render_widget(block, rect);

    let layout = Layout::default()
        .direction(Direction::Vertical)
        .constraints([
            Constraint::Length(1),
            Constraint::Length(2),
            Constraint::Length(1),
            Constraint::Length(1),
            Constraint::Length(1),
            Constraint::Length(1),
        ])
        .split(inner);

    let chip = |on: bool, label: String| {
        let style = if on {
            Style::default().fg(Color::Black).bg(ACCENT).add_modifier(Modifier::BOLD)
        } else {
            Style::default().fg(DIM)
        };
        Span::styled(format!(" {label} "), style)
    };
    let kinds = Line::from(vec![
        chip(kind == CustomKind::Site, crate::i18n::t!("tui.editor.kind_site").to_string()),
        Span::raw("  "),
        chip(kind == CustomKind::App, crate::i18n::t!("tui.editor.kind_app").to_string()),
    ]);
    f.render_widget(Paragraph::new(kinds).alignment(Alignment::Center), layout[0]);

    let hint = match kind {
        CustomKind::Site => crate::i18n::t!("tui.editor.add_site_hint"),
        CustomKind::App => crate::i18n::t!("tui.editor.add_app_hint"),
    };
    let hint_para = Paragraph::new(Span::styled(hint.to_string(), Style::default().fg(DIM)))
        .alignment(Alignment::Center);
    f.render_widget(hint_para, layout[1]);

    input.render(layout[2], f.buffer_mut(), Style::default().fg(TEXT));

    if let Some(err) = error {
        let err_line = Paragraph::new(Span::styled(err.to_string(), Style::default().fg(ALERT)))
            .alignment(Alignment::Center);
        f.render_widget(err_line, layout[4]);
    }

    let actions = Paragraph::new(Span::styled(
        crate::i18n::t!("tui.editor.add_actions"),
        Style::default().fg(DIM),
    ))
    .alignment(Alignment::Center);
    f.render_widget(actions, layout[5]);
}

fn draw_editor_fields(f: &mut Frame, area: Rect, editor: &EditorState) {
    let block = picker_block(" edit routine ");
    let inner = block.inner(area);
    f.render_widget(block, area);

    let fields: Vec<EditorField> =
        editor.visible_fields().into_iter().filter(|f| *f != EditorField::Blocklist).collect();
    let mut constraints: Vec<Constraint> = fields.iter().map(|_| Constraint::Length(2)).collect();
    constraints.push(Constraint::Min(3));
    let rows =
        Layout::default().direction(Direction::Vertical).constraints(constraints).split(inner);

    for (i, field) in fields.iter().enumerate() {
        draw_editor_field(f, rows[i], editor, *field);
    }

    let status_row = rows[fields.len()];
    let mut lines: Vec<Line> = Vec::new();
    if let Some(err) = &editor.error {
        lines.push(Line::from(Span::styled(
            err.clone(),
            Style::default().fg(ALERT).add_modifier(Modifier::BOLD),
        )));
    } else {
        lines.push(Line::from(Span::styled(
            editor.focus.help(),
            Style::default().fg(DIM).add_modifier(Modifier::ITALIC),
        )));
        if editor.focus == EditorField::Firmness {
            let limits = editor.build_profile().map(|(_, p)| p.limits).unwrap_or_default();
            lines.push(Line::from(Span::styled(
                editor.firmness().note(&limits),
                Style::default().fg(TEXT),
            )));
        }
    }
    if editor.is_dirty() {
        lines.push(Line::from(Span::styled(
            crate::i18n::t!("tui.editor.unsaved").to_string(),
            Style::default().fg(GLOW),
        )));
    }
    f.render_widget(Paragraph::new(lines).wrap(Wrap { trim: true }), status_row);
}

fn draw_editor_field(f: &mut Frame, area: Rect, editor: &EditorState, field: EditorField) {
    let focused = editor.focus == field;
    let is_error = editor.error_field == Some(field);
    let label_style = if is_error {
        Style::default().fg(ALERT).add_modifier(Modifier::BOLD)
    } else if focused {
        Style::default().fg(ACCENT).add_modifier(Modifier::BOLD)
    } else {
        Style::default().fg(DIM)
    };
    let prefix = if focused { "▶ " } else { "  " };
    let indent = if field.is_advanced() { "  " } else { "" };
    let label = format!("{prefix}{indent}{:<14}", field.label());
    let rows = Layout::default()
        .direction(Direction::Horizontal)
        .constraints([Constraint::Length(label.chars().count() as u16 + 1), Constraint::Min(5)])
        .split(area);

    f.render_widget(Paragraph::new(Line::from(Span::styled(label, label_style))), rows[0]);

    let value_style = if focused { Style::default().fg(TEXT) } else { Style::default().fg(DIM) };
    match field {
        EditorField::Color => draw_color_swatch(f, rows[1], editor, focused),
        EditorField::Firmness => {
            let spans =
                scale_spans(editor.firmness(), &[Firmness::Soft, Firmness::DelayedExit], focused);
            f.render_widget(Paragraph::new(Line::from(spans)), rows[1]);
        }
        EditorField::Schedule => {
            let text = match &editor.snapshot.schedule {
                Some(s) => fmt_schedule(s),
                None => crate::i18n::t!("tui.editor.schedule_none").to_string(),
            };
            let spans = vec![
                Span::styled(text, value_style),
                Span::styled(
                    format!("   {}", crate::i18n::t!("tui.editor.schedule_edit")),
                    Style::default().fg(DIM),
                ),
            ];
            f.render_widget(Paragraph::new(Line::from(spans)), rows[1]);
        }
        EditorField::Advanced => {
            let text = if editor.advanced_open {
                crate::i18n::t!("tui.editor.advanced_open").to_string()
            } else {
                crate::i18n::t!("tui.editor.advanced_closed").to_string()
            };
            f.render_widget(Paragraph::new(Span::styled(text, value_style)), rows[1]);
        }
        _ => {
            if let Some(input) = editor.input(field) {
                input.render(rows[1], f.buffer_mut(), value_style);
            }
        }
    }
}

fn draw_color_swatch(f: &mut Frame, area: Rect, editor: &EditorState, focused: bool) {
    let mut spans: Vec<Span> = Vec::new();
    for (i, (key, label)) in COLOR_PALETTE.iter().enumerate() {
        let is_sel = i == editor.color_idx;
        let col = palette_color(key);
        let mut style = Style::default().fg(col);
        if is_sel {
            style = style.add_modifier(Modifier::BOLD | Modifier::REVERSED);
        } else if !focused {
            style = style.add_modifier(Modifier::DIM);
        }
        spans.push(Span::styled(format!(" {label} "), style));
        spans.push(Span::raw(" "));
    }
    f.render_widget(Paragraph::new(Line::from(spans)), area);
}

fn draw_blocklist(f: &mut Frame, area: Rect, editor: &EditorState) {
    let count = editor.blocklist.selected.len();
    let base = crate::i18n::t!("tui.editor.blocklist_title", count = count).to_string();
    let title = if editor.blocklist.filter.is_empty() {
        format!(" {base} ")
    } else {
        format!(" {base} · /{} ", editor.blocklist.filter)
    };
    let focused = editor.focus == EditorField::Blocklist;
    editor.blocklist.render(area, f.buffer_mut(), picker_block(&title), focused);
}

pub fn palette_color(key: &str) -> Color {
    match key {
        "blue" => Color::Rgb(110, 160, 230),
        "cyan" => Color::Rgb(90, 200, 210),
        "green" => Color::Rgb(120, 200, 130),
        "amber" => Color::Rgb(220, 180, 90),
        "violet" => Color::Rgb(180, 140, 220),
        "red" => Color::Rgb(220, 110, 110),
        _ => DIM,
    }
}

fn fmt_opt_humantime(d: Option<Duration>) -> String {
    match d {
        Some(v) => humantime::format_duration(v).to_string(),
        None => String::new(),
    }
}

fn parse_opt_humantime(input: &TextInput) -> std::result::Result<Option<Duration>, String> {
    let raw = input.value.trim();
    if raw.is_empty() {
        return Ok(None);
    }
    humantime::parse_duration(raw)
        .map(Some)
        .map_err(|_| crate::i18n::t!("tui.editor.bad_duration", value = raw).to_string())
}

/// Cheap sanity check for a macOS/Linux app identifier (reverse-DNS like
/// `com.spotify.client`). We don't reject Windows-exe basenames here on
/// purpose — those can have any shape; if the user knows what they're doing
/// they can put one in. We just want to reject empty / accidental input.
fn looks_like_bundle_id(s: &str) -> bool {
    let s = s.trim();
    !s.is_empty()
        && !s.contains(char::is_whitespace)
        && (s.contains('.') || s.contains('-') || s.ends_with(".exe"))
}

fn split_cmds(raw: &str) -> Vec<String> {
    raw.split("&&").map(|s| s.trim().to_string()).filter(|s| !s.is_empty()).collect()
}

fn profile_eq(a: &Profile, b: &Profile) -> bool {
    a.sites == b.sites
        && a.site_groups == b.site_groups
        && a.brands == b.brands
        && a.apps == b.apps
        && a.allow == b.allow
        && a.hooks.before == b.hooks.before
        && a.hooks.after == b.hooks.after
        && a.limits.max_duration == b.limits.max_duration
        && a.limits.min_duration == b.limits.min_duration
        && a.limits.cooldown == b.limits.cooldown
        && a.limits.panic_delay == b.limits.panic_delay
        && a.color == b.color
        && a.schedule == b.schedule
}

fn site_item(host: &str) -> MultiSelectItem {
    let kind = crate::i18n::t!("tui.editor.kind_site");
    MultiSelectItem::new(format!("{SITE}{host}"), format!("◇ {host}  · {kind}"))
}

fn app_item(id: &str, label: Option<&str>) -> MultiSelectItem {
    let kind = crate::i18n::t!("tui.editor.kind_app");
    let text = match label {
        Some(l) => format!("▫ {l}  · {kind} [{id}]"),
        None => format!("▫ {id}  · {kind}"),
    };
    MultiSelectItem::new(format!("{APP}{id}"), text)
}

/// One list for everything a routine can block, in the order a person looks
/// for things: well-known services first (each covers its sites and its
/// app), then whole categories, then sites and apps of their own.
fn load_blocklist(profile: &Profile) -> MultiSelectList {
    let mut items: Vec<MultiSelectItem> = Vec::new();
    let mut selected: Vec<String> = Vec::new();

    let brand_kind = crate::i18n::t!("tui.editor.kind_brand").to_string();
    if let Ok(brands) = crate::brands::all_brands() {
        for b in brands {
            let q = b.qualified();
            items.push(MultiSelectItem::new(
                format!("{BRAND}{q}"),
                format!("{} {}  · {brand_kind}", b.icon.as_deref().unwrap_or("●"), b.name),
            ));
        }
    }
    for q in &profile.brands {
        let id = format!("{BRAND}{q}");
        if !items.iter().any(|i| i.id == id) {
            items.push(MultiSelectItem::new(id.clone(), format!("● {q}  · {brand_kind}")));
        }
        selected.push(id);
    }

    if let Ok(groups) = crate::sites::all_groups() {
        for g in groups {
            let q = g.qualified();
            let kind =
                crate::i18n::t!("tui.editor.kind_category", count = g.hosts.len()).to_string();
            items.push(MultiSelectItem::new(format!("{GROUP}{q}"), format!("▤ {q}  · {kind}")));
        }
    }
    for q in &profile.site_groups {
        let id = format!("{GROUP}{q}");
        if !items.iter().any(|i| i.id == id) {
            let kind = crate::i18n::t!("tui.editor.kind_category", count = 0).to_string();
            items.push(MultiSelectItem::new(id.clone(), format!("▤ {q}  · {kind}")));
        }
        selected.push(id);
    }

    for host in &profile.sites {
        items.push(site_item(host));
        selected.push(format!("{SITE}{host}"));
    }

    let mut app_ids: Vec<String> = Vec::new();
    if let Ok(cache) = crate::apps::load_or_scan(false) {
        for a in cache.apps {
            app_ids.push(a.id.clone());
            items.push(app_item(&a.id, Some(&a.label)));
        }
    }
    // Ids the scan does not know (added by hand earlier) must survive a save.
    for id in &profile.apps {
        if !app_ids.contains(id) {
            items.push(app_item(id, None));
        }
        selected.push(format!("{APP}{id}"));
    }

    MultiSelectList::new(items, &selected)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn blocklist_round_trips_every_kind() {
        let profile = Profile {
            sites: vec!["example.com".into()],
            apps: vec!["com.example.custom".into()],
            limits: Limits { daily_cap: Some(Duration::from_secs(3600)), ..Default::default() },
            ..Default::default()
        };
        let ed = EditorState::edit("x".into(), profile.clone());
        let (_, built) = ed.build_profile().unwrap();
        assert_eq!(built.sites, profile.sites);
        assert_eq!(built.apps, profile.apps);
        assert_eq!(built.limits.daily_cap, profile.limits.daily_cap);
        assert!(!ed.is_dirty());
    }

    #[test]
    fn firmness_is_a_view_over_cooldown() {
        let mut ed = EditorState::new_mode();
        assert_eq!(ed.firmness(), Firmness::Soft);
        ed.set_firmness(Firmness::DelayedExit);
        assert_eq!(ed.firmness(), Firmness::DelayedExit);
        assert_eq!(ed.cooldown.value, humantime::format_duration(DEFAULT_COOLDOWN).to_string());
        ed.set_firmness(Firmness::Soft);
        assert!(ed.cooldown.value.is_empty());
    }

    #[test]
    fn advanced_fields_hidden_until_opened() {
        let mut ed = EditorState::new_mode();
        assert!(!ed.visible_fields().contains(&EditorField::Max));
        ed.toggle_advanced();
        assert!(ed.visible_fields().contains(&EditorField::Max));
    }
}
