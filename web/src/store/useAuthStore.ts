import { create } from 'zustand';

type Profile = {
  email?: string;
  name?: string;
  roles?: string[];
};

type AuthState = {
  profile?: Profile;
  setProfile: (profile?: Profile) => void;
};

export const useAuthStore = create<AuthState>((set) => ({
  profile: undefined,
  setProfile: (profile) => set({ profile }),
}));
