import request from './request'

/**
 * 通用文件上传（头像、观察附件等）。
 * 旧作品相关 API 模块（api/student/work.ts、api/public/work.ts、api/workAdapter.ts）已随旧作品体系下线。
 */
export function uploadFile(file: File, workId?: string | number) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/api/file/upload',
    method: 'post',
    data: formData,
    params: {
      workId: workId || undefined,
    },
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
