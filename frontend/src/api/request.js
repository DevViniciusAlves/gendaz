import api, { modoDemo } from './axiosConfig.js'

export async function requestOrLocal(requester, fallback) {
  if (modoDemo) return fallback()
  const response = await requester(api)
  return response.data
}
