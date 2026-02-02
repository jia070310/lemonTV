#!/usr/bin/env python3
import os
import sys
import json
import base64
import hashlib
import requests
from datetime import datetime, timedelta

# 配置
REPO_OWNER = "ioptu"
REPO_NAME = "IPTV.txt2m3u.player"
FILE_PATH = "migu.m3u"
RAW_URL = f"https://github.com/{REPO_OWNER}/{REPO_NAME}/raw/refs/heads/main/{FILE_PATH}"
API_URL = f"https://api.github.com/repos/{REPO_OWNER}/{REPO_NAME}/commits"

LOCAL_FILE = "iptv-fe.m3u"
STATE_FILE = ".sync-state.json"

def get_remote_commit_info():
    """使用 GitHub API 获取文件最新 commit 时间"""
    api_endpoint = f"{API_URL}?path={FILE_PATH}&page=1&per_page=1"
    headers = {}
    
    # 如果有 GITHUB_TOKEN，使用它增加 API 限额（5000次/小时 vs 60次/小时）
    if os.environ.get('GITHUB_TOKEN'):
        headers['Authorization'] = f"Bearer {os.environ.get('GITHUB_TOKEN')}"
        print("使用 GITHUB_TOKEN 认证")
    
    try:
        resp = requests.get(api_endpoint, headers=headers, timeout=30)
        resp.raise_for_status()
        commits = resp.json()
        
        if not commits:
            print("错误：未找到文件的 commit 历史")
            sys.exit(1)
            
        latest_commit = commits[0]
        commit_data = latest_commit['commit']
        
        # 优先使用 committer date（推送到仓库的时间），更准确
        commit_time_str = commit_data['committer']['date']
        commit_time = datetime.fromisoformat(commit_time_str.replace('Z', '+00:00'))
        commit_time = commit_time.replace(tzinfo=None)  # 转为 UTC naive
        
        # 获取文件内容 hash（用于检测内容是否真的变化）
        file_sha = latest_commit['sha']
        
        # 获取文件内容
        content_resp = requests.get(RAW_URL, timeout=30)
        content_resp.raise_for_status()
        
        return commit_time, file_sha, content_resp.content
        
    except Exception as e:
        print(f"获取远程文件信息失败: {e}")
        sys.exit(1)

def load_state():
    if os.path.exists(STATE_FILE):
        try:
            with open(STATE_FILE, 'r', encoding='utf-8') as f:
                return json.load(f)
        except:
            return {}
    return {}

def save_state(state):
    with open(STATE_FILE, 'w', encoding='utf-8') as f:
        json.dump(state, f, indent=2, ensure_ascii=False)

def parse_time(time_str):
    if not time_str:
        return None
    try:
        dt = datetime.fromisoformat(time_str)
        if dt.tzinfo:
            dt = dt.replace(tzinfo=None)
        return dt
    except:
        return None

def main():
    now = datetime.utcnow()
    print(f"当前 UTC 时间: {now}")
    
    # 获取远程 commit 时间和内容
    commit_time, commit_sha, content = get_remote_commit_info()
    print(f"API 返回 Commit 时间 (UTC): {commit_time}")
    print(f"Commit SHA: {commit_sha[:8]}...")
    
    # 计算内容的 MD5 用于双重验证
    content_md5 = hashlib.md5(content).hexdigest()
    
    state = load_state()
    last_commit_time = parse_time(state.get('last_commit_time'))
    last_commit_sha = state.get('last_commit_sha')
    next_sync_time = parse_time(state.get('next_sync_time'))
    
    print(f"上次记录 Commit 时间: {last_commit_time}")
    print(f"上次记录 Commit SHA: {last_commit_sha[:8] if last_commit_sha else None}...")
    print(f"计划下次同步时间: {next_sync_time}")

    should_sync = False
    sync_reason = ""
    
    # 判断逻辑1：首次运行
    if not last_commit_time:
        should_sync = True
        sync_reason = "首次运行或状态重置"
    
    # 判断逻辑2：commit 时间或 SHA 变化（文件已更新）
    elif commit_time != last_commit_time or commit_sha != last_commit_sha:
        time_diff = abs((commit_time - last_commit_time).total_seconds()) if last_commit_time else 0
        if time_diff > 60 or commit_sha != last_commit_sha:
            should_sync = True
            sync_reason = f"远程文件已更新 (时间差 {time_diff/60:.1f} 分钟, SHA 变化: {commit_sha != last_commit_sha})"
        else:
            print("时间差异在容差范围内，视为未变更")
    
    # 判断逻辑3：到达计划同步时间（即使文件未变，也执行周期性同步）
    elif next_sync_time and now >= next_sync_time:
        should_sync = True
        sync_reason = f"到达计划同步时间 ({next_sync_time})"

    if should_sync:
        print(f"\n✅ 执行同步: {sync_reason}")
        
        # 写入文件
        with open(LOCAL_FILE, 'wb') as f:
            f.write(content)
        print(f"已更新 {LOCAL_FILE}")
        
        # 计算下次同步时间：以 commit 时间为基准 + 2小时
        new_next_sync = commit_time + timedelta(hours=2)
        
        # 如果 commit 时间已经过去太久（比如脚本延迟执行），则更扁当前时间+2小时
        if new_next_sync <= now:
            new_next_sync = now + timedelta(hours=2)
            print(f"注意：基准时间已过，从当前时间顺延2小时")
        
        new_state = {
            "last_commit_time": commit_time.isoformat(),
            "last_commit_sha": commit_sha,
            "content_md5": content_md5,
            "last_sync_time": now.isoformat(),
            "next_sync_time": new_next_sync.isoformat(),
            "sync_reason": sync_reason
        }
        
        save_state(new_state)
        print(f"下次同步时间设定为: {new_next_sync} UTC")
        
        # GitHub Actions 输出
        with open(os.environ.get('GITHUB_OUTPUT', '/dev/null'), 'a') as fh:
            print(f"should_commit=true", file=fh)
            print(f"sync_time={now.isoformat()}", file=fh)
            print(f"commit_time={commit_time.isoformat()}", file=fh)
    else:
        print(f"\n⏭️ 跳过同步")
        print(f"原因: 文件未变更且未到计划时间 ({next_sync_time})")
        
        with open(os.environ.get('GITHUB_OUTPUT', '/dev/null'), 'a') as fh:
            print(f"should_commit=false", file=fh)

if __name__ == "__main__":
    main()
