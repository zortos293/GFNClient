/** Error message entry with title and description */
export interface ErrorMessageEntry {
  title: string;
  description: string;
}

/** User-friendly error messages map */
export const ERROR_MESSAGES: Map<number, ErrorMessageEntry> = new Map([
  // Success
  [15859712, { title: "Success", description: "Session started successfully." }],

  // Client errors
  [
    3237085186,
    {
      title: "Invalid Operation",
      description: "The requested operation is not valid at this time.",
    },
  ],
  [
    3237089282,
    {
      title: "Network Error",
      description: "A network error occurred. Please check your internet connection.",
    },
  ],
  [
    3237093377,
    {
      title: "Authentication Required",
      description: "Your session has expired. Please log in again.",
    },
  ],
  [
    3237093379,
    {
      title: "Server Response Error",
      description: "Failed to parse server response. Please try again.",
    },
  ],
  [
    3237093381,
    {
      title: "Invalid Server Response",
      description: "The server returned an invalid response.",
    },
  ],
  [
    3237093384,
    {
      title: "Session Error",
      description: "An error occurred during session setup.",
    },
  ],
  [
    3237093387,
    {
      title: "Authentication Timeout",
      description: "Authentication token update timed out. Please log in again.",
    },
  ],

  // Server errors
  [
    3237093634,
    {
      title: "Access Forbidden",
      description: "Access to this service is forbidden.",
    },
  ],
  [
    3237093635,
    {
      title: "Server Timeout",
      description: "The server timed out. Please try again.",
    },
  ],
  [
    3237093636,
    {
      title: "Server Error",
      description: "An internal server error occurred. Please try again later.",
    },
  ],
  [
    3237093637,
    {
      title: "Invalid Request",
      description: "The request was invalid.",
    },
  ],
  [
    3237093639,
    {
      title: "Too Many Sessions",
      description: "You have too many active sessions. Please close some sessions and try again.",
    },
  ],
  [
    3237093643,
    {
      title: "Session Limit Exceeded",
      description: "You have reached your session limit. Another session may already be running on your account.",
    },
  ],
  [
    3237093645,
    {
      title: "Session Time Exceeded",
      description: "Your session time has been exceeded.",
    },
  ],
  [
    3237093646,
    {
      title: "Authentication Failed",
      description: "Authentication failed. Please log in again.",
    },
  ],
  [
    3237093648,
    {
      title: "Session Expired",
      description: "Your authentication has expired. Please log in again.",
    },
  ],
  [
    3237093650,
    {
      title: "Entitlement Error",
      description: "You don't have access to this game or service.",
    },
  ],
  [
    3237093651,
    {
      title: "Game Not Available",
      description: "This game is not currently available.",
    },
  ],
  [
    3237093652,
    {
      title: "Game Not Found",
      description: "This game was not found in the library.",
    },
  ],
  [
    3237093655,
    {
      title: "EULA Required",
      description: "You must accept the End User License Agreement to continue.",
    },
  ],
  [
    3237093656,
    {
      title: "Under Maintenance",
      description: "The service is currently under maintenance. Please try again later.",
    },
  ],
  [
    3237093657,
    {
      title: "Service Unavailable",
      description: "The service is temporarily unavailable. Please try again later.",
    },
  ],
  [
    3237093658,
    {
      title: "Steam Guard Required",
      description: "Steam Guard authentication is required. Please complete Steam Guard verification.",
    },
  ],
  [
    3237093659,
    {
      title: "Steam Login Required",
      description: "You need to link your Steam account to play this game.",
    },
  ],
  [
    3237093660,
    {
      title: "Steam Guard Invalid",
      description: "Steam Guard code is invalid. Please try again.",
    },
  ],
  [
    3237093661,
    {
      title: "Steam Profile Private",
      description: "Your Steam profile is private. Please make it public or friends-only.",
    },
  ],
  [
    3237093667,
    {
      title: "Email Not Verified",
      description: "Please verify your email address to continue.",
    },
  ],
  [
    3237093673,
    {
      title: "Game Updating",
      description: "This game is currently being updated. Please try again later.",
    },
  ],
  [
    3237093674,
    {
      title: "Game Not Found",
      description: "This game was not found.",
    },
  ],
  [
    3237093675,
    {
      title: "Insufficient Credits",
      description: "You don't have enough credits for this session.",
    },
  ],
  [
    3237093678,
    {
      title: "Session Taken Over",
      description: "Your session was taken over by another device.",
    },
  ],
  [
    3237093681,
    {
      title: "Session Expired",
      description: "Your session has expired.",
    },
  ],
  [
    3237093682,
    {
      title: "Device Limit Reached",
      description: "You have reached the session limit for this device.",
    },
  ],
  [
    3237093683,
    {
      title: "Region At Capacity",
      description: "Your region is currently at capacity. Please try again later.",
    },
  ],
  [
    3237093684,
    {
      title: "Region Not Supported",
      description: "The service is not available in your region.",
    },
  ],
  [
    3237093685,
    {
      title: "Region Banned",
      description: "The service is not available in your region.",
    },
  ],
  [
    3237093686,
    {
      title: "Free Tier On Hold",
      description: "Free tier is temporarily unavailable in your region.",
    },
  ],
  [
    3237093687,
    {
      title: "Paid Tier On Hold",
      description: "Paid tier is temporarily unavailable in your region.",
    },
  ],
  [
    3237093688,
    {
      title: "Game Maintenance",
      description: "This game is currently under maintenance.",
    },
  ],
  [
    3237093690,
    {
      title: "No Capacity",
      description: "No gaming rigs are available right now. Please try again later or join the queue.",
    },
  ],
  [
    3237093694,
    {
      title: "Queue Full",
      description: "The queue is currently full. Please try again later.",
    },
  ],
  [
    3237093695,
    {
      title: "GeForce NOW Unavailable in Your Region",
      description:
        "GeForce NOW has restricted streaming in your region. This is not an OpenNOW issue — NVIDIA has blocked access from your location. You may need to use a VPN or check GeForce NOW's supported countries list.",
    },
  ],
  [
    3237093698,
    {
      title: "Game Not Available",
      description: "This game is not available in your region.",
    },
  ],
  [
    3237093701,
    {
      title: "Queue Abandoned",
      description: "Your session in queue was abandoned.",
    },
  ],
  [
    3237093702,
    {
      title: "Account Terminated",
      description: "Your account has been terminated.",
    },
  ],
  [
    3237093703,
    {
      title: "Queue Maintenance",
      description: "The queue was cleared due to maintenance.",
    },
  ],
  [
    3237093704,
    {
      title: "Zone Maintenance",
      description: "This server zone is under maintenance.",
    },
  ],
  [
    3237093711,
    {
      title: "Ads Timeout",
      description: "Session expired while waiting for ads. Free tier users must watch ads to play. Please start a new session.",
    },
  ],
  [
    3237093712,
    {
      title: "Ads Cancelled",
      description: "Session cancelled because ads were skipped. Free tier users must watch ads to play.",
    },
  ],
  [
    3237093713,
    {
      title: "Limited Mode",
      description: "Streaming is not allowed in limited mode.",
    },
  ],
  [
    3237093715,
    {
      title: "Session Limit",
      description: "Maximum number of sessions reached.",
    },
  ],
  [
    3237093717,
    {
      title: "No Capacity",
      description: "No gaming rigs are available. Please try again later.",
    },
  ],
  [
    3237093718,
    {
      title: "Membership Upgrade Required",
      description: "Your current GeForce NOW membership is not high enough to play this game. Upgrade to a higher tier and try again.",
    },
  ],
  [
    3237093721,
    {
      title: "Storage Unavailable",
      description: "User storage is not available.",
    },
  ],
  [
    3237093722,
    {
      title: "Storage Error",
      description: "Service storage is not available.",
    },
  ],
  [
    3237093723,
    {
      title: "Streaming Not Allowed",
      description: "This app is not allowed to stream on your current GeForce NOW account or region.",
    },
  ],

  // Cancellation
  [
    15867905,
    {
      title: "Session Cancelled",
      description: "Session setup was cancelled.",
    },
  ],
  [
    15867906,
    {
      title: "Queue Cancelled",
      description: "You left the queue.",
    },
  ],
  [
    15867907,
    {
      title: "Request Cancelled",
      description: "The request was cancelled.",
    },
  ],
  [
    15867909,
    {
      title: "System Sleep",
      description: "Session setup was interrupted by system sleep.",
    },
  ],
  [
    15868417,
    {
      title: "No Internet",
      description: "No internet connection during session setup.",
    },
  ],

  // Network errors
  [
    3237101580,
    {
      title: "Socket Error",
      description: "A socket error occurred. Please check your network.",
    },
  ],
  [
    3237101581,
    {
      title: "DNS Error",
      description: "Failed to resolve server address. Please check your network.",
    },
  ],
  [
    3237101582,
    {
      title: "Connection Failed",
      description: "Failed to connect to the server. Please check your network.",
    },
  ],
  [
    3237101583,
    {
      title: "SSL Error",
      description: "A secure connection error occurred.",
    },
  ],
  [
    3237101584,
    {
      title: "Connection Timeout",
      description: "Connection timed out. Please check your network.",
    },
  ],
  [
    3237101585,
    {
      title: "Receive Timeout",
      description: "Data receive timed out. Please check your network.",
    },
  ],
  [
    3237101586,
    {
      title: "No Response",
      description: "Server not responding. Please try again.",
    },
  ],
  [
    3237101590,
    {
      title: "Certificate Error",
      description: "Server certificate was rejected.",
    },
  ],
]);

