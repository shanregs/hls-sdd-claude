import { api } from "./httpClient";

export interface AccountProfile {
  id: string;
  displayName: string;
  phone: string;
  username: string | null;
  email: string | null;
  roles: string[];
}

export interface ProfileUpdate {
  displayName: string;
  username: string;
  email: string;
}

export const getProfile = () => api.get<AccountProfile>("/api/v1/me/profile");

export const updateProfile = (body: ProfileUpdate) => api.put<AccountProfile>("/api/v1/me/profile", body);
