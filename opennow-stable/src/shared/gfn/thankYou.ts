export interface ThankYouContributor {
  login: string;
  avatarUrl: string;
  profileUrl: string;
  contributions: number;
}

export interface ThankYouSupporter {
  name: string;
  avatarUrl?: string;
  profileUrl?: string;
  isPrivate: boolean;
  source: "github" | "custom" | "private";
}

export interface ThankYouDataResult {
  contributors: ThankYouContributor[];
  supporters: ThankYouSupporter[];
  contributorsError?: string;
  supportersError?: string;
}
