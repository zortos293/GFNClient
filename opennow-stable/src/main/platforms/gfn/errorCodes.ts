import type { SessionErrorInfo } from "@shared/sessionError";
import { GfnErrorCode } from "./gfnErrorCodeEnum";
import { ERROR_MESSAGES } from "./gfnErrorMessages";

export { GfnErrorCode } from "./gfnErrorCodeEnum";
export { ERROR_MESSAGES } from "./gfnErrorMessages";
export type { ErrorMessageEntry } from "./gfnErrorMessages";

/**
 * CloudMatch error codes.
 *
 * These mappings provide user-friendly messages for session failures.
 */

/** CloudMatch error response structure */
interface CloudMatchErrorResponse {
  requestStatus?: {
    statusCode?: number;
    statusDescription?: string;
    unifiedErrorCode?: number;
  };
  session?: {
    sessionId?: string;
    errorCode?: number;
  };
}

/** Session error class for parsing and handling CloudMatch errors */
export class SessionError extends Error {
  /** HTTP status code */
  public readonly httpStatus: number;
  /** CloudMatch status code from requestStatus.statusCode */
  public readonly statusCode: number;
  /** Status description from requestStatus.statusDescription */
  public readonly statusDescription?: string;
  /** Unified error code from requestStatus.unifiedErrorCode */
  public readonly unifiedErrorCode?: number;
  /** Session error code from session.errorCode */
  public readonly sessionErrorCode?: number;
  /** Computed service error code */
  public readonly gfnErrorCode: number;
  /** User-friendly title */
  public readonly title: string;

  constructor(info: SessionErrorInfo) {
    super(info.description);
    this.name = "SessionError";
    this.httpStatus = info.httpStatus;
    this.statusCode = info.statusCode;
    this.statusDescription = info.statusDescription;
    this.unifiedErrorCode = info.unifiedErrorCode;
    this.sessionErrorCode = info.sessionErrorCode;
    this.gfnErrorCode = info.gfnErrorCode;
    this.title = info.title;
  }

  /** Get error type as a string (e.g., "SessionLimitExceeded") */
  get errorType(): string {
    // Try to find the enum name from the error code
    const entry = Object.entries(GfnErrorCode).find(([, value]) => value === this.gfnErrorCode);
    if (entry) {
      return entry[0];
    }
    // Fallback to status code based naming
    if (this.statusCode > 0) {
      return `StatusCode${this.statusCode}`;
    }
    return "UnknownError";
  }

  /** Get user-friendly error message */
  get errorDescription(): string {
    return this.message;
  }

  /**
   * Parse error from CloudMatch response JSON
   */
  static fromResponse(httpStatus: number, responseBody: string): SessionError {
    let json: CloudMatchErrorResponse = {};

    try {
      json = JSON.parse(responseBody) as CloudMatchErrorResponse;
    } catch {
      // Parsing failed, use empty object
    }

    // Extract fields
    const statusCode = json.requestStatus?.statusCode ?? 0;
    const statusDescription = json.requestStatus?.statusDescription;
    const unifiedErrorCode = json.requestStatus?.unifiedErrorCode;
    const sessionErrorCode = json.session?.errorCode;

    // Compute normalized service error code
    const gfnErrorCode = SessionError.computeErrorCode(statusCode, unifiedErrorCode);

    // Get user-friendly message
    const { title, description } = SessionError.getErrorMessage(
      gfnErrorCode,
      statusDescription,
      httpStatus,
    );

    return new SessionError({
      httpStatus,
      statusCode,
      statusDescription,
      unifiedErrorCode,
      sessionErrorCode,
      gfnErrorCode,
      title,
      description,
    });
  }

  /**
   * Compute service error code from CloudMatch response
   */
  private static computeErrorCode(statusCode: number, unifiedErrorCode?: number): number {
    // Base error code
    let errorCode: number = 3237093632; // SessionServerErrorBegin

    // Convert statusCode to error code
    if (statusCode === 1) {
      errorCode = 15859712; // Success
    } else if (statusCode > 0 && statusCode < 255) {
      errorCode = 3237093632 + statusCode;
    }

    // Use unifiedErrorCode if available and error_code is generic
    if (unifiedErrorCode !== undefined) {
      switch (errorCode) {
        case 3237093632: // SessionServerErrorBegin
        case 3237093636: // ServerInternalError
        case 3237093381: // InvalidServerResponse
          errorCode = unifiedErrorCode;
          break;
      }
    }

    return errorCode;
  }

  /**
   * Get user-friendly error message
   */
  private static getErrorMessage(
    errorCode: number,
    statusDescription: string | undefined,
    httpStatus: number,
  ): { title: string; description: string } {
    // Check for known error code
    const knownError = ERROR_MESSAGES.get(errorCode);
    if (knownError) {
      return knownError;
    }

    // Parse status description for known patterns
    if (statusDescription) {
      const descUpper = statusDescription.toUpperCase();

      if (descUpper.includes("INSUFFICIENT_PLAYABILITY")) {
        return {
          title: "Membership Upgrade Required",
          description:
            "Your current GeForce NOW membership is not high enough to play this game. Upgrade to a higher tier and try again.",
        };
      }

      if (descUpper.includes("SESSION_LIMIT")) {
        return {
          title: "Session Limit Exceeded",
          description: "You have reached your maximum number of concurrent sessions.",
        };
      }

      if (descUpper.includes("MAINTENANCE")) {
        return {
          title: "Under Maintenance",
          description: "The service is currently under maintenance. Please try again later.",
        };
      }

      if (descUpper.includes("CAPACITY") || descUpper.includes("QUEUE")) {
        return {
          title: "No Capacity Available",
          description: "All gaming rigs are currently in use. Please try again later.",
        };
      }

      if (descUpper.includes("AUTH") || descUpper.includes("TOKEN")) {
        return {
          title: "Authentication Error",
          description: "Please log in again.",
        };
      }

      if (descUpper.includes("ENTITLEMENT")) {
        return {
          title: "Access Denied",
          description: "You don't have access to this game or service.",
        };
      }
    }

    // Fallback based on HTTP status
    switch (httpStatus) {
      case 401:
        return {
          title: "Unauthorized",
          description: "Please log in again.",
        };
      case 403:
        return {
          title: "Access Denied",
          description: "Access to this resource was denied.",
        };
      case 404:
        return {
          title: "Not Found",
          description: "The requested resource was not found.",
        };
      case 429:
        return {
          title: "Too Many Requests",
          description: "Please wait a moment and try again.",
        };
    }

    if (httpStatus >= 500 && httpStatus < 600) {
      return {
        title: "Server Error",
        description: "A server error occurred. Please try again later.",
      };
    }

    return {
      title: "Error",
      description: `An error occurred (HTTP ${httpStatus}).`,
    };
  }

  /**
   * Check if this error indicates another session is running
   */
  isSessionConflict(): boolean {
    const sessionConflictCodes = [
      GfnErrorCode.SessionLimitExceeded, // 3237093643
      GfnErrorCode.SessionLimitPerDeviceReached, // 3237093682
      GfnErrorCode.MaxSessionNumberLimitExceeded, // 3237093715
    ];

    if (sessionConflictCodes.includes(this.gfnErrorCode)) {
      return true;
    }

    return false;
  }

  /**
   * Check if this is a temporary error that might resolve with retry
   */
  isRetryable(): boolean {
    const retryableCodes = [
      GfnErrorCode.NetworkError, // 3237089282
      GfnErrorCode.ServerInternalTimeout, // 3237093635
      GfnErrorCode.ServerInternalError, // 3237093636
      GfnErrorCode.ForwardingZoneOutOfCapacity, // 3237093683
      GfnErrorCode.InsufficientVmCapacity, // 3237093690
      GfnErrorCode.SessionRejectedNoCapacity, // 3237093717
      GfnErrorCode.ConnectionTimeout, // 3237101584
      GfnErrorCode.DataReceiveTimeout, // 3237101585
      GfnErrorCode.PeerNoResponse, // 3237101586
    ];

    return retryableCodes.includes(this.gfnErrorCode);
  }

  /**
   * Check if user needs to log in again
   */
  needsReauth(): boolean {
    const reauthCodes = [
      GfnErrorCode.AuthTokenNotUpdated, // 3237093377
      GfnErrorCode.AuthTokenUpdateTimeout, // 3237093387
      GfnErrorCode.AuthFailure, // 3237093646
      GfnErrorCode.InvalidAuthenticationMalformed, // 3237093647
      GfnErrorCode.InvalidAuthenticationExpired, // 3237093648
      GfnErrorCode.InvalidAuthenticationNotFound, // 3237093649
      GfnErrorCode.InvalidAuthenticationUnsupportedProtocol, // 3237093668
      GfnErrorCode.InvalidAuthenticationUnknownToken, // 3237093669
      GfnErrorCode.InvalidAuthenticationCredentials, // 3237093670
    ];

    if (reauthCodes.includes(this.gfnErrorCode)) {
      return true;
    }

    if (this.httpStatus === 401) {
      return true;
    }

    return false;
  }

  /**
   * Convert to a plain object for serialization
   */
  toJSON(): SessionErrorInfo {
    return {
      httpStatus: this.httpStatus,
      statusCode: this.statusCode,
      statusDescription: this.statusDescription,
      unifiedErrorCode: this.unifiedErrorCode,
      sessionErrorCode: this.sessionErrorCode,
      gfnErrorCode: this.gfnErrorCode,
      title: this.title,
      description: this.message,
    };
  }
}

/** Helper function to check if an error is a SessionError */
export function isSessionError(error: unknown): error is SessionError {
  return error instanceof SessionError;
}

/** Helper function to parse error from CloudMatch response */
export function parseCloudMatchError(httpStatus: number, responseBody: string): SessionError {
  return SessionError.fromResponse(httpStatus, responseBody);
}
