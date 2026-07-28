export type DiscordActivityKind = "queued" | "starting" | "streaming";

export interface DiscordActivityUpdate {
  gameName: string;
  gameImageUrl?: string;
  kind: DiscordActivityKind;
  appId?: string;
  queuePosition?: number;
  startTimestampMs?: number;
}

export interface DiscordGameArtwork {
  imageUrl?: string;
  heroImageUrl?: string;
  imageUrlsByType?: Record<string, string[]>;
}

const DISCORD_GAME_IMAGE_TYPES = ["GAME_BOX_ART", "KEY_IMAGE", "KEY_ART"] as const;

export function discordGameImageUrl(game: DiscordGameArtwork): string | undefined {
  for (const imageType of DISCORD_GAME_IMAGE_TYPES) {
    const imageUrl = game.imageUrlsByType?.[imageType]?.find((candidate) => candidate.trim().length > 0);
    if (imageUrl) {
      return imageUrl;
    }
  }
  return game.imageUrl || game.heroImageUrl;
}
