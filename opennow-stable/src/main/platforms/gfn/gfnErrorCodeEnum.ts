/** Session error code constants. */
export enum GfnErrorCode {
  // Success codes
  Success = 15859712,

  // Client-side errors (3237085xxx - 3237093xxx)
  InvalidOperation = 3237085186,
  NetworkError = 3237089282,
  GetActiveSessionServerError = 3237089283,
  AuthTokenNotUpdated = 3237093377,
  SessionFinishedState = 3237093378,
  ResponseParseFailure = 3237093379,
  InvalidServerResponse = 3237093381,
  PutOrPostInProgress = 3237093382,
  GridServerNotInitialized = 3237093383,
  DOMExceptionInSessionControl = 3237093384,
  InvalidAdStateTransition = 3237093386,
  AuthTokenUpdateTimeout = 3237093387,

  // Server error codes (base 3237093632 + statusCode)
  SessionServerErrorBegin = 3237093632,
  RequestForbidden = 3237093634, // statusCode 2
  ServerInternalTimeout = 3237093635, // statusCode 3
  ServerInternalError = 3237093636, // statusCode 4
  ServerInvalidRequest = 3237093637, // statusCode 5
  ServerInvalidRequestVersion = 3237093638, // statusCode 6
  SessionListLimitExceeded = 3237093639, // statusCode 7
  InvalidRequestDataMalformed = 3237093640, // statusCode 8
  InvalidRequestDataMissing = 3237093641, // statusCode 9
  RequestLimitExceeded = 3237093642, // statusCode 10
  SessionLimitExceeded = 3237093643, // statusCode 11
  InvalidRequestVersionOutOfDate = 3237093644, // statusCode 12
  SessionEntitledTimeExceeded = 3237093645, // statusCode 13
  AuthFailure = 3237093646, // statusCode 14
  InvalidAuthenticationMalformed = 3237093647, // statusCode 15
  InvalidAuthenticationExpired = 3237093648, // statusCode 16
  InvalidAuthenticationNotFound = 3237093649, // statusCode 17
  EntitlementFailure = 3237093650, // statusCode 18
  InvalidAppIdNotAvailable = 3237093651, // statusCode 19
  InvalidAppIdNotFound = 3237093652, // statusCode 20
  InvalidSessionIdMalformed = 3237093653, // statusCode 21
  InvalidSessionIdNotFound = 3237093654, // statusCode 22
  EulaUnAccepted = 3237093655, // statusCode 23
  MaintenanceStatus = 3237093656, // statusCode 24
  ServiceUnAvailable = 3237093657, // statusCode 25
  SteamGuardRequired = 3237093658, // statusCode 26
  SteamLoginRequired = 3237093659, // statusCode 27
  SteamGuardInvalid = 3237093660, // statusCode 28
  SteamProfilePrivate = 3237093661, // statusCode 29
  InvalidCountryCode = 3237093662, // statusCode 30
  InvalidLanguageCode = 3237093663, // statusCode 31
  MissingCountryCode = 3237093664, // statusCode 32
  MissingLanguageCode = 3237093665, // statusCode 33
  SessionNotPaused = 3237093666, // statusCode 34
  EmailNotVerified = 3237093667, // statusCode 35
  InvalidAuthenticationUnsupportedProtocol = 3237093668, // statusCode 36
  InvalidAuthenticationUnknownToken = 3237093669, // statusCode 37
  InvalidAuthenticationCredentials = 3237093670, // statusCode 38
  SessionNotPlaying = 3237093671, // statusCode 39
  InvalidServiceResponse = 3237093672, // statusCode 40
  AppPatching = 3237093673, // statusCode 41
  GameNotFound = 3237093674, // statusCode 42
  NotEnoughCredits = 3237093675, // statusCode 43
  InvitationOnlyRegistration = 3237093676, // statusCode 44
  RegionNotSupportedForRegistration = 3237093677, // statusCode 45
  SessionTerminatedByAnotherClient = 3237093678, // statusCode 46
  DeviceIdAlreadyUsed = 3237093679, // statusCode 47
  ServiceNotExist = 3237093680, // statusCode 48
  SessionExpired = 3237093681, // statusCode 49
  SessionLimitPerDeviceReached = 3237093682, // statusCode 50
  ForwardingZoneOutOfCapacity = 3237093683, // statusCode 51
  RegionNotSupportedIndefinitely = 3237093684, // statusCode 52
  RegionBanned = 3237093685, // statusCode 53
  RegionOnHoldForFree = 3237093686, // statusCode 54
  RegionOnHoldForPaid = 3237093687, // statusCode 55
  AppMaintenanceStatus = 3237093688, // statusCode 56
  ResourcePoolNotConfigured = 3237093689, // statusCode 57
  InsufficientVmCapacity = 3237093690, // statusCode 58
  InsufficientRouteCapacity = 3237093691, // statusCode 59
  InsufficientScratchSpaceCapacity = 3237093692, // statusCode 60
  RequiredSeatInstanceTypeNotSupported = 3237093693, // statusCode 61
  ServerSessionQueueLengthExceeded = 3237093694, // statusCode 62
  RegionNotSupportedForStreaming = 3237093695, // statusCode 63
  SessionForwardRequestAllocationTimeExpired = 3237093696, // statusCode 64
  SessionForwardGameBinariesNotAvailable = 3237093697, // statusCode 65
  GameBinariesNotAvailableInRegion = 3237093698, // statusCode 66
  UekRetrievalFailed = 3237093699, // statusCode 67
  EntitlementFailureForResource = 3237093700, // statusCode 68
  SessionInQueueAbandoned = 3237093701, // statusCode 69
  MemberTerminated = 3237093702, // statusCode 70
  SessionRemovedFromQueueMaintenance = 3237093703, // statusCode 71
  ZoneMaintenanceStatus = 3237093704, // statusCode 72
  GuestModeCampaignDisabled = 3237093705, // statusCode 73
  RegionNotSupportedAnonymousAccess = 3237093706, // statusCode 74
  InstanceTypeNotSupportedInSingleRegion = 3237093707, // statusCode 75
  InvalidZoneForQueuedSession = 3237093710, // statusCode 78
  SessionWaitingAdsTimeExpired = 3237093711, // statusCode 79
  UserCancelledWatchingAds = 3237093712, // statusCode 80
  StreamingNotAllowedInLimitedMode = 3237093713, // statusCode 81
  ForwardRequestJPMFailed = 3237093714, // statusCode 82
  MaxSessionNumberLimitExceeded = 3237093715, // statusCode 83
  GuestModePartnerCapacityDisabled = 3237093716, // statusCode 84
  SessionRejectedNoCapacity = 3237093717, // statusCode 85
  SessionInsufficientPlayabilityLevel = 3237093718, // statusCode 86
  ForwardRequestLOFNFailed = 3237093719, // statusCode 87
  InvalidTransportRequest = 3237093720, // statusCode 88
  UserStorageNotAvailable = 3237093721, // statusCode 89
  GfnStorageNotAvailable = 3237093722, // statusCode 90
  AppNotAllowedToStream = 3237093723, // statusCode 91
  SessionServerErrorEnd = 3237093887,

  // Session setup cancelled
  SessionSetupCancelled = 15867905,
  SessionSetupCancelledDuringQueuing = 15867906,
  RequestCancelled = 15867907,
  SystemSleepDuringSessionSetup = 15867909,
  NoInternetDuringSessionSetup = 15868417,

  // Network errors (3237101xxx)
  SocketError = 3237101580,
  AddressResolveFailed = 3237101581,
  ConnectFailed = 3237101582,
  SslError = 3237101583,
  ConnectionTimeout = 3237101584,
  DataReceiveTimeout = 3237101585,
  PeerNoResponse = 3237101586,
  UnexpectedHttpRedirect = 3237101587,
  DataSendFailure = 3237101588,
  DataReceiveFailure = 3237101589,
  CertificateRejected = 3237101590,
  DataNotAllowed = 3237101591,
  NetworkErrorUnknown = 3237101592,
}

