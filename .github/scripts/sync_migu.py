#!/usr/bin/env python3
import os
import sys
import json
import requests
from datetime import datetime, timedelta
from email.utils import parsedate_to_datetime

# 配置
REMOTE_URL = "https://github.com/ioptu/IPTV.txt2m3u.player/raw/refs/heads/main/migu.m3u"
LOCAL_FILE = "iptv-fe.m3u"
STATE_FILE = ".sync-state.json"

def get_remote_info():
    """获取远程文件的修改时间和内容"""
    try:
        # 获取头部信息
        head_resp = requests.head(REMOTE_URL, allow_redirects=True, timeout=30)
        head_resp.raise_for_status()
        
        last_modified = head_resp.headers.get('Last-Modified')
        if not last_modified:
            print("警告：无法获取 Last-Modified，使用当前时间作为基准")
            remote_time = datetime.utcnow()
        else:
            # 解析 GMT 时间并转为 UTC
            remote_time = parsedate_to_datetime(last_modified)
            if remote_time.tzinfo:
                remote_time = remote_time.replace(tzinfo=None)
        
        # 获取内容
        content_resp = requests.get(REMOTE_URL, timeout=30)
        content_resp.raise_for_status()
        
        return remote_time, content_resp.content
    except Exception as e:
        print(f"获取远程文件失败: {e}")
        sys.exit(1)

def load_state():
    """加载本地状态"""
    if os.path.exists(STATE_FILE):
        try:
            with open(STATE_FILE, 'r', encoding='utf-8') as f:
                return json.load(f)
        except:
            return {}
    return {}

def save_state(state):
    """保存状态到文件"""
    with open(STATE_FILE, 'w', encoding='utf-8') as f:
        json.dump(state, f, indent=2, ensure_ascii=False)

def parse_time(time_str):
    """将 ISO 格式字符串转回 datetime"""
    if not time_str:
        return None
    try:
        return datetime.fromisoformat(time_str)
    except:
        return None

def main():
    now = datetime.utcnow()
    print(f"当前 UTC 时间: {now}")
    
    # 获取远程信息
    remote_time, content = get_remote_info()
    print(f"远程文件时间 (UTC): {remote_time}")
    
    # 加载历史状态
    state = load_state()
    last_remote_time = parse_time(state.get('last_remote_time'))
    next_sync_time = parse_time(state.get('next_sync_time'))
    
    print(f"上次记录远程时间: {last_remote_time}")
    print(f"计划下次同步时间: {next_sync_time}")
    
    should_sync = False
    sync_reason = ""
    
    # 情况1：首次运行或状态丢失
    if not last_remote_time:
        should_sync = True
        sync_reason = "首次运行或状态重置"
    
    # 情况2：远程文件已更新（时间戳变化）
    elif remote_time != last_remote_time:
        # 考虑到秒级精度可能不够，允许1分钟误差
        time_diff = abs((remote_time - last_remote_time).total_seconds())
        if time_diff > 60:
            should_sync = True
            sync_reason = f"远程文件已更新（时间差 {time_diff/60:.1f} 分钟）"
        else:
            print("时间差异在容差范围内，视为未变更")
    
    # 情况3：到达计划同步时间
    elif next_sync_time and now >= next_sync_time:
        should_sync = True
        sync_reason = f"到达计划同步时间 ({next_sync_time})"
    
    if should_sync:
        print(f"\n✅ 执行同步: {sync_reason}")
        
        # 写入文件
        with open(LOCAL_FILE, 'wb') as f:
            f.write(content)
        print(f"已更新 {LOCAL_FILE}")
        
        # 计算下次同步时间：以远程文件时间为基准 + 2小时
        new_next_sync = remote_time + timedelta(hours=2)
        
        # 如果计算出的下次时间已经过去（比如延迟执行），则顺延到下一个周期
        if new_next_sync <= now:
            new_next_sync = now + timedelta(hours=2)
            print(f"注意：基准时间已过，从当前时间顺延2小时")
        
        new_state = {
            "last_remote_time": remote_time.isoformat(),
            "last_sync_time": now.isoformat(),
            "next_sync_time": new_next_sync.isoformat(),
            "sync_reason": sync_reason
        }
        
        save_state(new_state)
        print(f"下次同步时间设定为: {new_next_sync} UTC")
        
        # 设置 GitHub Actions 输出变量，供后续步骤使用
        with open(os.environ.get('GITHUB_OUTPUT', '/dev/null'), 'a') as fh:
            print(f"should_commit=true", file=fh)
            print(f"sync_time={now.isoformat()}", file=fh)
    else:
        print(f"\n⏭️ 跳过同步")
        print(f"原因: 远程未变更且未到计划时间 ({next_sync_time})")
        
        with open(os.environ.get('GITHUB_OUTPUT', '/dev/null'), 'a') as fh:
            print(f"should_commit=false", file=fh)

if __name__ == "__main__":
    main()
