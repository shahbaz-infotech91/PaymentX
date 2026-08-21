/**
 * ENGLISH: The real API client for the Phase 4 Files domain. What it
 * does: lists real files in the one, server-configured directory
 * (FilesController.list()) and builds a real, safe download URL for
 * one of them - mirrors backend dto/files/FileEntry.java exactly. No
 * function here accepts a directory path from its caller (only a bare
 * filename that the backend's SafeFileService independently validates
 * before ever touching disk), matching the Phase 4 brief's "do NOT
 * expose arbitrary filesystem browsing" requirement. Why it exists:
 * the frontend must never construct a filesystem path itself. How it
 * will communicate with the backend: real HTTP calls to
 * FilesController.
 *
 * HINGLISH: Phase 4 ke Files domain ke liye real API client. Ye kya
 * karti hai: us ek, server-configured directory ki real files list
 * karta hai (FilesController.list()) aur unme se ek ke liye ek real,
 * safe download URL banata hai - backend dto/files/FileEntry.java ko
 * exactly mirror karti hai. Yahan koi function apne caller se directory
 * path accept nahi karta (sirf ek bare filename, jise backend ka
 * SafeFileService disk touch karne se pehle independently validate
 * karta hai), Phase 4 brief ke "arbitrary filesystem browsing expose
 * mat karo" requirement se match karte hue. Ye dashboard me kyu hai:
 * frontend ko kabhi khud ek filesystem path construct nahi karna
 * chahiye. Backend se kaise connect hogi: FilesController ko real HTTP
 * calls.
 */
import { axiosClient } from '../api/axiosClient'
import type { ApiResponse } from '../types/common'

export interface FileEntry {
  name: string
  sizeBytes: number
  lastModified: string
}

export async function fetchFiles(): Promise<FileEntry[]> {
  const response = await axiosClient.get<ApiResponse<FileEntry[]>>('/api/v1/files')
  return response.data.data ?? []
}

/** The real, safe absolute download URL for one file - resolved against the same baseURL every other real backend call uses. */
export function downloadFileUrl(filename: string): string {
  const base = axiosClient.defaults.baseURL ?? ''
  return `${base}/api/v1/files/download/${encodeURIComponent(filename)}`
}
