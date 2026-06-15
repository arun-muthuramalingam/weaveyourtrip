#!/bin/bash

# Read JSON input from stdin
input=$(cat)

# Extract values from JSON
model=$(echo "$input" | jq -r '.model.display_name // "Claude"')
cwd=$(echo "$input" | jq -r '.workspace.current_dir // ""')
session_name=$(echo "$input" | jq -r '.session_name // empty')
remaining=$(echo "$input" | jq -r '.context_window.remaining_percentage // empty')

# Extract token usage info
total_input=$(echo "$input" | jq -r '.context_window.total_input_tokens // 0')
total_output=$(echo "$input" | jq -r '.context_window.total_output_tokens // 0')
current_input=$(echo "$input" | jq -r '.context_window.current_usage.input_tokens // empty')
current_output=$(echo "$input" | jq -r '.context_window.current_usage.output_tokens // empty')
cache_read=$(echo "$input" | jq -r '.context_window.current_usage.cache_read_input_tokens // empty')

# Get username and hostname
user=$(whoami)
host=$(hostname -s)

# Get git branch if in a git repo (skip optional locks for safety)
git_info=""
if git -C "$cwd" rev-parse --git-dir >/dev/null 2>&1; then
    branch=$(git -C "$cwd" -c core.fileMode=false -c gc.autodetach=false branch --show-current 2>/dev/null)
    if [ -n "$branch" ]; then
        if ! git -C "$cwd" -c core.fileMode=false diff --quiet 2>/dev/null || \
           ! git -C "$cwd" -c core.fileMode=false diff --cached --quiet 2>/dev/null; then
            git_info="[$branch*] "
        else
            git_info="[$branch] "
        fi
    fi
fi

# Format large numbers with K suffix for readability
format_tokens() {
    local tokens=$1
    if [ "$tokens" -ge 1000 ]; then
        echo "$((tokens / 1000))k"
    else
        echo "$tokens"
    fi
}

# Build status line
# PS1 style from .bashrc: bold-green user@host, colon, bold-blue cwd
output=""

# user@host (bold green, matching .bashrc PS1: \[\033[01;32m\]\u@\h\[\033[00m\])
output+=$(printf "\033[01;32m%s@%s\033[00m:" "$user" "$host")

# Current directory (bold blue, matching .bashrc PS1: \[\033[01;34m\]\w\[\033[00m\])
output+=$(printf "\033[01;34m%s\033[00m" "$cwd")

# Session name if set
if [ -n "$session_name" ]; then
    output+=$(printf " \033[36m[%s]\033[0m" "$session_name")
fi

# Git info
if [ -n "$git_info" ]; then
    output+=" $git_info"
fi

# Model info (cyan)
output+=$(printf " \033[36m%s\033[0m" "$model")

# Current message token usage (last API call)
if [ -n "$current_input" ] && [ "$current_input" != "null" ]; then
    current_in_fmt=$(format_tokens "$current_input")
    current_out_fmt=$(format_tokens "$current_output")
    output+=$(printf " \033[35m[in:%s out:%s" "$current_in_fmt" "$current_out_fmt")

    if [ -n "$cache_read" ] && [ "$cache_read" != "null" ] && [ "$cache_read" -gt 0 ]; then
        cache_fmt=$(format_tokens "$cache_read")
        output+=$(printf " cache:%s" "$cache_fmt")
    fi
    output+=$(printf "]\033[0m")
fi

# Total session token usage
if [ "$total_input" -gt 0 ] || [ "$total_output" -gt 0 ]; then
    total_in_fmt=$(format_tokens "$total_input")
    total_out_fmt=$(format_tokens "$total_output")
    output+=$(printf " \033[90m[total: in:%s out:%s]\033[0m" "$total_in_fmt" "$total_out_fmt")
fi

# Context window remaining (yellow if under 30%)
if [ -n "$remaining" ] && [ "$remaining" != "null" ]; then
    if [ "${remaining%.*}" -lt 30 ]; then
        output+=$(printf " \033[33m[ctx:%s%%]\033[0m" "$remaining")
    else
        output+=$(printf " [ctx:%s%%]" "$remaining")
    fi
fi

echo "$output"
