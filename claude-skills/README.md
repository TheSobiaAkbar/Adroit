# Claude skills for Adroit

Two [Claude Code skills](https://docs.claude.com/en/docs/claude-code/skills) that teach Claude how to integrate Adroit
into an Android app. With them installed, Claude doesn't need this repository as context.

| Skill | Use it for |
|---|---|
| `adroit-admob` | AdMob ads: every format, the splash and consent, the in-app update, Firebase control of each ad, testing and release. |
| `adroit-billing` | In-app purchases: products, entitlements, paywalls (built-in and custom), Firebase-controlled paywalls, restore, tests, Play Console setup and release. |

An app that has both ads and purchases uses both skills. Claude picks them up from the request ("add a banner",
"add a paywall"), or you can name them.

## Install

The skills are user-level, so every project on the machine sees them:

```bash
cp -r claude-skills/adroit-admob claude-skills/adroit-billing ~/.claude/skills/
```

On Windows (PowerShell):

```powershell
Copy-Item -Recurse -Force claude-skills\adroit-admob, claude-skills\adroit-billing $HOME\.claude\skills\
```

Start a new Claude Code session. Both skills then appear in its skill list.

## Keep them in sync with the library

The skills are verified against a specific Adroit version, which each `SKILL.md` states (currently `1.0.0-rc7`). When a change alters the public API, a default, or a recommended pattern:

1. Update the matching `references/*.md` file, and the version line in `SKILL.md`.
2. Check that every API name in the skills still exists. For example, take the backticked identifiers in the skill
   files and grep each one against the `*/api/*.api` dumps and `adroit-*/src/main`.
3. Copy the folders to `~/.claude/skills/` again.

The skills also record lessons from apps built on Adroit (PhotoRecovery), such as the app open skip-on-leave pattern
and the R8 `packageScope` fix. Add new ones in the same way: the symptom, the cause, and the fix.
