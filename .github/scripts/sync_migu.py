#!/usr/bin/env python3
import os
import sys
import json
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

def get_remote_info():
    """获取远程文件信息"""
    api_endpoint = f"{API_URL}?path={FILE_PATH}&page=1&per_page=1"
    headers = {}
    
    if os.environ.get('GITHUB_TOKEN'):
        headers['Authorization'] = f"Bearer {os.environ.get('GITHUB_TOKEN')}"
    
    try:
        resp = requests.get(api_endpoint, headers=headers, timeout=30)
        resp.raise_for_status()
        commits = resp.json()
        
        if not commits:
            print("错误：未找到文件 commit 历史")
            sys.exit(1)
            
        latest_commit = commits[0]
        commit_data = latest_commit['commit']
        
        # 获取 commit 时间（UTC）
        commit_time_str = commit_data['committer']['date']
        commit_time = datetime.fromisoformat(commit_time_str.replace('Z', '+00:00'))
        commit_time = commit_time.replace(tzinfo=None)  # 转为 naive UTC
        
        # 获取文件内容
        content_resp = requests.get(RAW_URL, timeout=30)
        content_resp.raise_for_status()
        
        # 计算从 commit 到现在过了多久（分钟）
        now = datetime.utcnow()
        minutes_since_commit = int((now - commit_time).total_seconds() / 60)
        
        return {
            'commit_time': commit_time,
            'commit_sha': latest_commit['sha'],
            'minutes_since_commit': minutes_since_commit,
            'content': content_resp.content
        }
        
    except Exception as e:
        print(f"获取远程信息失败: {e}")
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
    
    # 获取远程信息
    remote = get_remote_info()
    commit_time = remote['commit_time']
    current_sha = remote['commit_sha']
    
    print(f"远程 Commit 时间 (UTC): {commit_time}")
    print(f"距离 commit 已过去: {remote['minutes_since_commit']} 分钟")
    print(f"Commit SHA: {current_sha[:8]}")

    # 加载状态
    state = load_state()
    tracked_sha = state.get('tracked_commit_sha')  # 我们正在跟踪的 commit
    scheduled_sync_time = parse_time(state.get('scheduled_sync_time'))
    
    print(f"当前跟踪的 SHA: {tracked_sha[:8] if tracked_sha else None}")
    print(f"计划同步时间: {scheduled_sync_time}")

    # 判断是否发现了新的更新（SHA 变化）
    is_new_update = (current_sha != tracked_sha)
    
    if is_new_update:
        print(f"\n🆕 检测到新的 commit！")
        # 发现了新更新，立即锁定基准，但**不立即同步**
        # 设定 2 小时后的同步时间
        sync_time = commit_time + timedelta(hours=2)
        
        # 如果 commit 时间已经超过 2 小时（即 sync_time 已过），则立即同步
        if sync_time <= now:
            print(f"Commit 已是 2 小时前，立即同步")
            should_sync_now = True
        else:
            print(f"设定未来同步时间: {sync_time} UTC")
            should_sync_now = False
            
        # 保存状态（锁定这个新 commit）
        new_state = {
            'tracked_commit_sha': current_sha,
            'tracked_commit_time': commit_time.isoformat(),
            'scheduled_sync_time': sync_time.isoformat(),
            'detected_at': now.isoformat(),
            'minutes_since_commit_at_detection': remote['minutes_since_commit']
        }
        save_state(new_state)
        
        if should_sync_now:
            print(f"\n✅ 执行同步（commit 已过期）")
        else:
            print(f"\n⏳ 已锁定基准，等待 {sync_time} 再同步")
            # 输出变量供 workflow 使用
            with open(os.environ.get('GITHUB_OUTPUT', '/dev/null'), 'a') as fh:
                print(f"should_commit=false", file=fh)
                print(f"status=scheduled", file=fh)
                print(f"scheduled_time={sync_time.isoformat()}", file=fh)
            return
    
    # SHA 没有变化，检查是否到达同步时间
    elif scheduled_sync_time:
        if now >= scheduled_sync_time:
            print(f"\n✅ 到达计划同步时间！执行同步")
            should_sync_now = True
        else:
            minutes_left = int((scheduled_sync_time - now).total_seconds() / 60)
            print(f"\n⏳ 还未到时间，还剩 {minutes_left} 分钟")
            with open(os.environ.get('GITHUB_OUTPUT', '/dev/null'), 'a') as fh:
                print(f"should_commit=false", file=fh)
                print(f"status=waiting", file=fh)
                print(f"minutes_left={minutes_left}", file=fh)
            return
    else:
        print(f"\n⚠️ 无状态且未检测到新 commit，跳过")
        with open(os.environ.get('GITHUB_OUTPUT', '/dev/null'), 'a') as fh:
            print(f"should_commit=false", file=fh)
        return

    # 执行同步
    if should_sync_now:
        with open(LOCAL_FILE, 'wb') as f:
            f.write(remote['content'])
        print(f"已更新 {LOCAL_FILE}")
        
        # 更新状态为已同步
        state['last_sync_time'] = now.isoformat()
        state['sync_reason'] = f"按计划同步（基准: {commit_time}）"
        save_state(state)
        
        # GitHub Actions 输出
        with open(os.environ.get('GITHUB_OUTPUT', '/dev/null'), 'a') as fh:
            print(f"should_commit=true", file=fh)
            print(f"status=synced", file=fh)
            print(f"base_time={commit_time.isoformat()}", file=fh)
            print(f"sync_time={now.isoformat()}", file=fh)

if __name__ == "__main__":
    main()
