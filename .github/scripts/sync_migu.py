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
        commit_time = commit_time.replace(tzinfo=None)
        
        # 获取文件内容
        content_resp = requests.get(RAW_URL, timeout=30)
        content_resp.raise_for_status()
        
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
    
    remote = get_remote_info()
    commit_time = remote['commit_time']
    current_sha = remote['commit_sha']
    
    print(f"远程 Commit 时间 (UTC): {commit_time}")
    print(f"距离 commit 已过去: {remote['minutes_since_commit']} 分钟")
    print(f"Commit SHA: {current_sha[:8]}")

    state = load_state()
    tracked_sha = state.get('tracked_commit_sha')
    scheduled_sync_time = parse_time(state.get('scheduled_sync_time'))
    last_sync_time = parse_time(state.get('last_sync_time'))
    
    print(f"当前跟踪的 SHA: {tracked_sha[:8] if tracked_sha else None}")
    print(f"上次同步时间: {last_sync_time}")
    print(f"计划同步时间: {scheduled_sync_time}")

    # 判断是否有新 commit
    is_new_commit = (current_sha != tracked_sha)
    
    # 计算基于 commit 时间的理论同步时间
    theoretical_sync_time = commit_time + timedelta(hours=2)
    
    should_sync = False
    sync_reason = ""

    if is_new_commit:
        print(f"\n🆕 检测到新的 commit！")
        
        # 新 commit，锁定基准
        if theoretical_sync_time > now:
            # commit 很新，设定未来同步
            scheduled_sync_time = theoretical_sync_time
            print(f"设定未来同步时间: {scheduled_sync_time} UTC")
            should_sync = False
        else:
            # commit 已经超过 2 小时，立即同步
            print(f"Commit 已过期 {remote['minutes_since_commit']} 分钟，立即同步")
            should_sync = True
            
        # 保存状态（锁定这个新 commit）
        new_state = {
            'tracked_commit_sha': current_sha,
            'tracked_commit_time': commit_time.isoformat(),
            'scheduled_sync_time': scheduled_sync_time.isoformat() if scheduled_sync_time else None,
            'detected_at': now.isoformat(),
            'already_synced': should_sync  # 标记是否已经同步过
        }
        save_state(new_state)
        
    else:
        # 同 commit，检查是否需要同步
        print(f"\n📋 同 commit，检查同步状态")
        
        already_synced = state.get('already_synced', False)
        
        if already_synced:
            print(f"该 commit 已同步过，跳过")
            should_sync = False
        elif scheduled_sync_time and now >= scheduled_sync_time:
            print(f"✅ 到达计划同步时间！")
            should_sync = True
            sync_reason = "按计划时间同步"
        elif not scheduled_sync_time:
            # 没有设定时间（兼容旧状态），检查是否超过 2 小时
            if theoretical_sync_time <= now:
                print(f"✅ 超过 2 小时，立即同步")
                should_sync = True
                sync_reason = "超过2小时自动同步"
            else:
                # 设定时间
                scheduled_sync_time = theoretical_sync_time
                print(f"⏳ 设定同步时间: {scheduled_sync_time}")
                should_sync = False
                # 保存
                state['scheduled_sync_time'] = scheduled_sync_time.isoformat()
                save_state(state)
        else:
            minutes_left = int((scheduled_sync_time - now).total_seconds() / 60)
            print(f"⏳ 等待中，还剩 {minutes_left} 分钟")

    # 执行同步
    if should_sync:
        print(f"\n🔄 执行同步: {sync_reason}")
        
        with open(LOCAL_FILE, 'wb') as f:
            f.write(remote['content'])
        print(f"已更新 {LOCAL_FILE}")
        
        # 更新状态
        state['last_sync_time'] = now.isoformat()
        state['already_synced'] = True
        state['sync_reason'] = sync_reason
        if 'scheduled_sync_time' in state:
            del state['scheduled_sync_time']  # 清除计划时间
        save_state(state)
        
        with open(os.environ.get('GITHUB_OUTPUT', '/dev/null'), 'a') as fh:
            print(f"should_commit=true", file=fh)
            print(f"sync_time={now.isoformat()}", file=fh)
            print(f"base_time={commit_time.isoformat()}", file=fh)
    else:
        print(f"\n⏭️ 跳过同步")
        with open(os.environ.get('GITHUB_OUTPUT', '/dev/null'), 'a') as fh:
            print(f"should_commit=false", file=fh)

if __name__ == "__main__":
    main()
