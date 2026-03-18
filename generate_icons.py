#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
图标生成脚本
将原始图标添加边距并生成各种尺寸
"""

from PIL import Image
import os

# 配置
ICON_SIZES = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192,
}

# 图标内容占比（留出边距）
CONTENT_RATIO = 0.75  # 75% 为图标内容，25% 为边距

def create_icon_with_padding(source_path, output_path, size):
    """
    创建带边距的图标
    
    Args:
        source_path: 源图标路径
        output_path: 输出路径
        size: 目标尺寸
    """
    # 打开原图
    source = Image.open(source_path)
    
    # 计算内容区域大小
    content_size = int(size * CONTENT_RATIO)
    
    # 调整原图大小到内容区域
    source_resized = source.resize((content_size, content_size), Image.Resampling.LANCZOS)
    
    # 创建透明背景的新图
    result = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    
    # 计算居中位置
    offset = (size - content_size) // 2
    
    # 将缩小的图标粘贴到中心
    result.paste(source_resized, (offset, offset), source_resized)
    
    # 保存
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    result.save(output_path, 'PNG', optimize=True)
    print(f"已生成: {output_path} ({size}x{size})")

def main():
    # 源文件路径
    source_icon = 'icon_original.png'
    
    if not os.path.exists(source_icon):
        print(f"错误: 找不到源图标文件 {source_icon}")
        return
    
    # 生成各种尺寸的图标
    res_dir = 'app/src/main/res'
    
    for folder, size in ICON_SIZES.items():
        output_path = os.path.join(res_dir, folder, 'ic_launcher.png')
        create_icon_with_padding(source_icon, output_path, size)
    
    print("\n✅ 所有图标生成完成！")
    print(f"图标内容占比: {int(CONTENT_RATIO * 100)}%")
    print(f"边距占比: {int((1 - CONTENT_RATIO) * 100)}%")

if __name__ == '__main__':
    main()
