(function() {
  if (typeof window === 'undefined') return;

  // Check if window.openNow is already defined
  if (window.openNow) {
    console.log("[webOS Bridge] window.openNow is already defined, skipping shim.");
    return;
  }

  console.log("[webOS Bridge] Injecting webOS GFN shim...");

  // Error trapping to display on-screen diagnostic messages
  window.onerror = function(message, source, lineno, colno, error) {
    var errorMsg = "Error: " + message + "\nSource: " + source + "\nLine: " + lineno + ":" + colno;
    if (error && error.stack) {
      errorMsg += "\nStack: " + error.stack;
    }
    showDebugError(errorMsg);
    return false;
  };

  try {
    window.addEventListener('unhandledrejection', function(event) {
      var reason = event.reason;
      var msg = reason;
      if (reason instanceof Error) {
        msg = reason.message + "\nStack: " + reason.stack;
      } else if (typeof reason === 'object') {
        try {
          msg = JSON.stringify(reason);
        } catch (e) {}
      }
      showDebugError("Unhandled Rejection: " + msg);
    });
  } catch (e) {}

  function showDebugError(msg) {
    var div = document.getElementById("debug-error-overlay");
    if (!div) {
      div = document.createElement("div");
      div.id = "debug-error-overlay";
      div.style.position = "fixed";
      div.style.top = "10px";
      div.style.left = "10px";
      div.style.right = "10px";
      div.style.bottom = "10px";
      div.style.background = "rgba(180, 0, 0, 0.95)";
      div.style.color = "white";
      div.style.padding = "20px";
      div.style.fontFamily = "monospace";
      div.style.fontSize = "16px";
      div.style.lineHeight = "1.4";
      div.style.zIndex = "999999";
      div.style.overflow = "auto";
      div.style.whiteSpace = "pre-wrap";
      div.style.borderRadius = "8px";
      div.style.border = "3px solid white";
      
      if (document.body) {
        document.body.appendChild(div);
      } else {
        document.addEventListener("DOMContentLoaded", function() {
          document.body.appendChild(div);
        });
      }
    }
    div.textContent = (div.textContent ? div.textContent + "\n\n" : "") + msg;
  }

  var LCARS_CLIENT_ID = "ec7e38d4-03af-4b58-b131-cfb0495903ab";
  var GFN_CLIENT_VERSION = "2.0.80.173";
  var GRAPHQL_URL = "https://games.geforce.com/graphql";

  // Base client headers builder
  function buildHeaders(token) {
    var headers = {
      "Accept": "application/json, text/plain, */*",
      "Content-Type": "application/json",
      "nv-client-id": LCARS_CLIENT_ID,
      "nv-client-type": "NATIVE",
      "nv-client-version": GFN_CLIENT_VERSION,
      "nv-client-streamer": "NVIDIA-CLASSIC",
      "nv-device-os": "LINUX",
      "nv-device-type": "DESKTOP"
    };
    if (token) {
      headers["Authorization"] = "GFNJWT " + token;
    }
    return headers;
  }

  // Settings management
  var defaultSettings = {
    resolution: "1920x1080",
    fps: 60,
    maxBitrateMbps: 75,
    codec: "H264",
    decoderPreference: "auto",
    encoderPreference: "auto",
    colorQuality: "10bit_420",
    region: "",
    clipboardPaste: false,
    mouseSensitivity: 1,
    shortcutToggleStats: "F3",
    shortcutTogglePointerLock: "F8",
    shortcutStopStream: "Ctrl+Shift+Q",
    shortcutToggleAntiAfk: "Ctrl+Shift+K",
    shortcutToggleMicrophone: "Ctrl+Shift+M",
    microphoneMode: "disabled",
    microphoneDeviceId: "",
    hideStreamButtons: false,
    sessionClockShowEveryMinutes: 60,
    sessionClockShowDurationSeconds: 30,
    windowWidth: 1400,
    windowHeight: 900,
    touchGamepadLayout: "{}"
  };

  function getSettingsInternal() {
    var saved = localStorage.getItem("opennow_settings");
    if (!saved) return defaultSettings;
    try {
      var parsed = JSON.parse(saved);
      var result = {};
      var key;
      for (key in defaultSettings) {
        if (defaultSettings.hasOwnProperty(key)) {
          result[key] = defaultSettings[key];
        }
      }
      for (key in parsed) {
        if (parsed.hasOwnProperty(key)) {
          result[key] = parsed[key];
        }
      }
      return result;
    } catch (e) {
      return defaultSettings;
    }
  }

  // Device Login State cache using ES5 objects instead of Map
  var deviceLoginAttempts = {};
  var pendingSessions = {};

  // Polyfill for generateUUID
  function generateUUID() {
    var d = new Date().getTime();
    var d2 = (typeof performance !== 'undefined' && performance.now && (performance.now() * 1000)) || 0;
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
      var r = Math.random() * 16;
      if (d > 0) {
        r = (d + r) % 16 | 0;
        d = Math.floor(d / 16);
      } else {
        r = (d2 + r) % 16 | 0;
        d2 = Math.floor(d2 / 16);
      }
      return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
    });
  }

  // Polyfill for fetch using XMLHttpRequest
  function httpFetch(url, options) {
    return new Promise(function(resolve, reject) {
      options = options || {};
      var xhr = new XMLHttpRequest();
      var method = options.method || "GET";
      xhr.open(method, url, true);
      
      var headers = options.headers || {};
      for (var key in headers) {
        if (headers.hasOwnProperty(key)) {
          xhr.setRequestHeader(key, headers[key]);
        }
      }
      
      xhr.onload = function() {
        var responseText = xhr.responseText;
        var status = xhr.status;
        var ok = status >= 200 && status < 300;
        
        var response = {
          ok: ok,
          status: status,
          text: function() { return Promise.resolve(responseText); },
          json: function() {
            try {
              return Promise.resolve(JSON.parse(responseText));
            } catch (e) {
              return Promise.reject(e);
            }
          }
        };
        resolve(response);
      };
      
      xhr.onerror = function() {
        reject(new Error("Network error during fetch to: " + url));
      };
      
      xhr.send(options.body || null);
    });
  }

  var api = {
    // Settings
    getSettings: function() {
      return Promise.resolve(getSettingsInternal());
    },
    setSetting: function(key, value) {
      var settings = getSettingsInternal();
      settings[key] = value;
      localStorage.setItem("opennow_settings", JSON.stringify(settings));
      return Promise.resolve();
    },
    resetSettings: function() {
      localStorage.removeItem("opennow_settings");
      return Promise.resolve(defaultSettings);
    },

    // Accounts
    getSavedAccounts: function() {
      var val = localStorage.getItem("opennow_saved_accounts");
      return Promise.resolve(val ? JSON.parse(val) : []);
    },
    getAuthSession: function() {
      var val = localStorage.getItem("opennow_auth_session");
      return Promise.resolve({
        session: val ? JSON.parse(val) : null,
        refresh: { outcome: "not_attempted" }
      });
    },
    switchAccount: function(userId) {
      var val = localStorage.getItem("opennow_saved_accounts");
      var accounts = val ? JSON.parse(val) : [];
      var acc = null;
      for (var i = 0; i < accounts.length; i++) {
        if (accounts[i].user.userId === userId) {
          acc = accounts[i];
          break;
        }
      }
      if (acc) {
        localStorage.setItem("opennow_auth_session", JSON.stringify(acc));
        return Promise.resolve(acc);
      }
      return Promise.reject(new Error("Account not found"));
    },
    removeAccount: function(userId) {
      var val = localStorage.getItem("opennow_saved_accounts");
      var accounts = val ? JSON.parse(val) : [];
      var filtered = [];
      for (var i = 0; i < accounts.length; i++) {
        if (accounts[i].user.userId !== userId) {
          filtered.push(accounts[i]);
        }
      }
      localStorage.setItem("opennow_saved_accounts", JSON.stringify(filtered));

      var active = localStorage.getItem("opennow_auth_session");
      if (active) {
        try {
          var activeSession = JSON.parse(active);
          if (activeSession.user.userId === userId) {
            localStorage.removeItem("opennow_auth_session");
          }
        } catch (e) {}
      }
      return Promise.resolve();
    },
    logout: function() {
      localStorage.removeItem("opennow_auth_session");
      return Promise.resolve();
    },
    logoutAll: function() {
      localStorage.removeItem("opennow_auth_session");
      localStorage.setItem("opennow_saved_accounts", "[]");
      return Promise.resolve();
    },

    // Auth Providers / Regions
    getLoginProviders: function() {
      return httpFetch("https://pcs.geforcenow.com/v1/serviceUrls")
        .then(function(res) {
          if (!res.ok) throw new Error("Failed to fetch service URLs");
          return res.json();
        })
        .then(function(data) {
          var endpoints = (data.gfnServiceInfo && data.gfnServiceInfo.gfnServiceEndpoints) || [];
          var mapped = [];
          for (var i = 0; i < endpoints.length; i++) {
            var entry = endpoints[i];
            mapped.push({
              idpId: entry.idpId,
              code: entry.loginProviderCode,
              displayName: entry.loginProviderCode === "BPC" ? "bro.game" : entry.loginProviderDisplayName,
              streamingServiceUrl: entry.streamingServiceUrl,
              priority: entry.loginProviderPriority || 0
            });
          }
          return mapped.sort(function(a, b) {
            return a.priority - b.priority;
          });
        })
        .catch(function(e) {
          console.warn("[webOS Bridge] Error loading login providers:", e);
          return [{ idpId: "NVIDIA", code: "NV", displayName: "GeForce NOW", streamingServiceUrl: "https://prod.cloudmatchbeta.nvidiagrid.net/", priority: 1 }];
        });
    },

    getRegions: function(input) {
      var baseUrl = (input && input.providerStreamingBaseUrl) || "https://prod.cloudmatchbeta.nvidiagrid.net/";
      var cleanUrl = baseUrl.replace(/\/+$/, "");
      var token = input && input.token;
      return httpFetch(cleanUrl + "/v2/serverInfo", {
        headers: buildHeaders(token)
      })
        .then(function(res) {
          if (!res.ok) return [];
          return res.json();
        })
        .then(function(data) {
          var zoneList = data.zoneList || [];
          var mapped = [];
          for (var i = 0; i < zoneList.length; i++) {
            mapped.push({
              name: zoneList[i].zoneName,
              url: zoneList[i].zoneUrl
            });
          }
          return mapped;
        })
        .catch(function(e) {
          console.warn("[webOS Bridge] Error loading regions:", e);
          return [];
        });
    },

    // Device Authorization OAuth Flow (QR/PIN Login)
    startDeviceLogin: function(input) {
      return api.getLoginProviders()
        .then(function(providers) {
          var provider = null;
          for (var i = 0; i < providers.length; i++) {
            if (providers[i].idpId === input.providerIdpId) {
              provider = providers[i];
              break;
            }
          }
          if (!provider) {
            provider = providers[0];
          }
          var deviceId = generateUUID();
          var body = "client_id=" + encodeURIComponent(LCARS_CLIENT_ID) +
                     "&scope=" + encodeURIComponent("openid offline_access credential") +
                     "&device_id=" + encodeURIComponent(deviceId) +
                     "&display_name=" + encodeURIComponent("OpenNOW webOS TV") +
                     "&idp_id=" + encodeURIComponent(provider.idpId);

          return httpFetch("https://login.nvidia.com/device/authorize", {
            method: "POST",
            headers: {
              "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
              "x-device-id": deviceId,
              "nv-client-id": LCARS_CLIENT_ID,
              "nv-client-streamer": "WEBRTC",
              "nv-client-type": "BROWSER"
            },
            body: body
          })
            .then(function(res) {
              if (!res.ok) {
                throw new Error("Failed to start device authorization");
              }
              return res.json();
            })
            .then(function(challenge) {
              var attemptId = generateUUID().replace(/-/g, "");
              deviceLoginAttempts[attemptId] = {
                provider: provider,
                deviceCode: challenge.device_code,
                expiresAt: Date.now() + (challenge.expires_in * 1000),
                deviceId: deviceId
              };

              return {
                attemptId: attemptId,
                userCode: challenge.user_code,
                verificationUrl: challenge.verification_uri,
                verificationUrlWithCode: challenge.verification_uri_complete,
                expiresIn: challenge.expires_in,
                interval: challenge.interval,
                deviceCode: challenge.device_code
              };
            });
        });
    },

    pollDeviceLogin: function(input) {
      var attempt = deviceLoginAttempts[input.attemptId];
      if (!attempt) return Promise.resolve({ status: "expired", error: "Device login expired" });
      if (Date.now() >= attempt.expiresAt) {
        delete deviceLoginAttempts[input.attemptId];
        return Promise.resolve({ status: "expired", error: "Device login expired" });
      }

      var body = "grant_type=" + encodeURIComponent("urn:ietf:params:oauth:grant-type:device_code") +
                 "&device_code=" + encodeURIComponent(attempt.deviceCode) +
                 "&client_id=" + encodeURIComponent(LCARS_CLIENT_ID);

      return httpFetch("https://login.nvidia.com/token", {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
          "x-device-id": attempt.deviceId,
          "nv-client-id": LCARS_CLIENT_ID
        },
        body: body
      })
        .then(function(res) {
          return res.json().then(function(data) {
            if (!res.ok) {
              if (data.error === "authorization_pending") {
                return { status: "pending" };
              }
              return { status: "error", error: data.error_description || "Login failed" };
            }

            // Parse user info from ID Token
            var token = data.id_token || data.access_token;
            var user = { userId: "unknown", displayName: "User", email: "", avatarUrl: "", membershipTier: "FREE" };
            try {
              var payload = JSON.parse(atob(token.split(".")[1]));
              var splitEmail = payload.email ? payload.email.split("@")[0] : "";
              user = {
                userId: payload.sub || "unknown",
                displayName: payload.preferred_username || splitEmail || "User",
                email: payload.email || "",
                avatarUrl: payload.picture || "",
                membershipTier: payload.gfn_tier || "FREE"
              };
            } catch (e) {
              console.error("[webOS Bridge] Error parsing JWT", e);
            }

            var tokens = {
              accessToken: data.access_token,
              refreshToken: data.refresh_token,
              idToken: data.id_token,
              expiresAt: Date.now() + (data.expires_in * 1000),
              authClientId: LCARS_CLIENT_ID,
              clientToken: data.client_token
            };

            var session = {
              provider: attempt.provider,
              tokens: tokens,
              user: user
            };

            pendingSessions[input.attemptId] = session;
            return { status: "authorized" };
          });
        });
    },

    completeDeviceLogin: function(input) {
      var session = pendingSessions[input.attemptId];
      if (!session) return Promise.reject(new Error("No authorized session found"));
      
      delete pendingSessions[input.attemptId];
      delete deviceLoginAttempts[input.attemptId];

      // Save to accounts
      var val = localStorage.getItem("opennow_saved_accounts");
      var accounts = val ? JSON.parse(val) : [];
      var existingIdx = -1;
      for (var i = 0; i < accounts.length; i++) {
        if (accounts[i].user.userId === session.user.userId) {
          existingIdx = i;
          break;
        }
      }
      if (existingIdx >= 0) {
        accounts[existingIdx] = session;
      } else {
        accounts.push(session);
      }
      localStorage.setItem("opennow_saved_accounts", JSON.stringify(accounts));
      localStorage.setItem("opennow_auth_session", JSON.stringify(session));

      return Promise.resolve(session);
    },

    cancelDeviceLogin: function(input) {
      delete deviceLoginAttempts[input.attemptId];
      delete pendingSessions[input.attemptId];
      return Promise.resolve();
    },

    login: function() {
      return Promise.reject(new Error("Standard login not supported on webOS TV. Please use QR Code / Device Login."));
    },

    // Catalog & Games (GraphQL client-side requests)
    browseCatalog: function(input) {
      var token = input.token;
      var base = input.providerStreamingBaseUrl || "https://prod.cloudmatchbeta.nvidiagrid.net/";
      var cleanUrl = base.replace(/\/+$/, "");

      return httpFetch(cleanUrl + "/v2/serverInfo", {
        headers: buildHeaders(token)
      })
        .then(function(res) {
          return res.json();
        })
        .then(function(serverInfo) {
          var vpcId = serverInfo.vpcId || "default";
          var variables = {
            vpcId: vpcId,
            locale: "en_US",
            sortString: "itemMetadata.relevance:DESC,sortName:ASC",
            fetchCount: input.fetchCount || 60,
            cursor: "",
            filters: {}
          };
          if (input.searchQuery) {
            variables.searchString = input.searchQuery;
          }

          var appFields = "\n" +
            "        numberReturned\n" +
            "        numberSupported\n" +
            "        pageInfo { hasNextPage endCursor totalCount }\n" +
            "        items {\n" +
            "          id\n" +
            "          title\n" +
            "          images { KEY_ART KEY_IMAGE GAME_BOX_ART TV_BANNER HERO_IMAGE MARQUEE_HERO_IMAGE FEATURE_IMAGE GAME_LOGO SCREENSHOTS }\n" +
            "          variants {\n" +
            "            id\n" +
            "            appStore\n" +
            "            storeUrl\n" +
            "            supportedControls\n" +
            "            gfn {\n" +
            "              status\n" +
            "              library { status selected }\n" +
            "            }\n" +
            "          }\n" +
            "          gfn {\n" +
            "            playabilityState\n" +
            "            minimumMembershipTierLabel\n" +
            "          }\n" +
            "          itemMetadata { campaignIds }\n" +
            "        }\n";

          var query = input.searchQuery
            ? "query GetSearchFilterResults($vpcId: String!, $locale: String!, $sortString: String!, $fetchCount: Int!, $cursor: String!, $searchString: String!, $filters: AppFilterFields!) {\n" +
              "            apps(vpcId: $vpcId, language: $locale, orderBy: $sortString, first: $fetchCount, after: $cursor, searchQuery: $searchString, filters: $filters) { " + appFields + " }\n" +
              "          }"
            : "query GetFilterBrowseResults($vpcId: String!, $locale: String!, $sortString: String!, $fetchCount: Int!, $cursor: String!, $filters: AppFilterFields!) {\n" +
              "            apps(vpcId: $vpcId, language: $locale, orderBy: $sortString, first: $fetchCount, after: $cursor, filters: $filters) { " + appFields + " }\n" +
              "          }";

          return httpFetch(GRAPHQL_URL, {
            method: "POST",
            headers: buildHeaders(token),
            body: JSON.stringify({ query: query, variables: variables })
          });
        })
        .then(function(res) {
          if (!res.ok) throw new Error("Catalog query failed");
          return res.json();
        })
        .then(function(body) {
          var appsData = (body.data && body.data.apps) || { items: [], pageInfo: { hasNextPage: false, totalCount: 0 } };
          return {
            games: appsData.items || [],
            hasNextPage: !!(appsData.pageInfo && appsData.pageInfo.hasNextPage),
            totalCount: (appsData.pageInfo && appsData.pageInfo.totalCount) || 0
          };
        });
    },

    fetchFeaturedGames: function(input) {
      var token = input.token;
      var base = input.providerStreamingBaseUrl || "https://prod.cloudmatchbeta.nvidiagrid.net/";
      var cleanUrl = base.replace(/\/+$/, "");

      return httpFetch(cleanUrl + "/v2/serverInfo", {
        headers: buildHeaders(token)
      })
        .then(function(res) {
          return res.json();
        })
        .then(function(serverInfo) {
          var vpcId = serverInfo.vpcId || "default";
          var variables = JSON.stringify({
            vpcId: vpcId,
            locale: "en_US",
            panelNames: ["MAIN"]
          });
          var extensions = JSON.stringify({
            persistedQuery: {
              sha256Hash: "f8e26265a5db5c20e1334a6872cf04b6e3970507697f6ae55a6ddefa5420daf0"
            }
          });
          var params = "requestType=" + encodeURIComponent("panels/MainV2") +
                       "&extensions=" + encodeURIComponent(extensions) +
                       "&huId=" + encodeURIComponent(Date.now().toString(16) + Math.random().toString(16).slice(2)) +
                       "&variables=" + encodeURIComponent(variables);

          var headers = buildHeaders(token);
          headers["Content-Type"] = "application/graphql";

          return httpFetch(GRAPHQL_URL + "?" + params, {
            headers: headers
          });
        })
        .then(function(res) {
          if (!res.ok) return [];
          return res.json().then(function(body) {
            var panels = (body.data && body.data.panels) || [];
            var games = [];
            for (var i = 0; i < panels.length; i++) {
              var panel = panels[i];
              if (panel.apps && panel.apps.items) {
                var items = panel.apps.items;
                for (var j = 0; j < items.length; j++) {
                  games.push(items[j]);
                }
              }
            }
            return games;
          });
        })
        .catch(function() {
          return [];
        });
    },

    fetchLibraryGames: function(input) {
      var token = input.token;
      var base = input.providerStreamingBaseUrl || "https://prod.cloudmatchbeta.nvidiagrid.net/";
      var cleanUrl = base.replace(/\/+$/, "");

      return httpFetch(cleanUrl + "/v2/serverInfo", {
        headers: buildHeaders(token)
      })
        .then(function(res) {
          return res.json();
        })
        .then(function(serverInfo) {
          var vpcId = serverInfo.vpcId || "default";
          var variables = JSON.stringify({
            vpcId: vpcId,
            locale: "en_US",
            panelNames: ["LIBRARY"]
          });
          var extensions = JSON.stringify({
            persistedQuery: {
              sha256Hash: "039e8c0d553972975485fee56e59f2549d2fdb518e247a42ab5022056a74406f"
            }
          });
          var params = "requestType=" + encodeURIComponent("panels/Library") +
                       "&extensions=" + encodeURIComponent(extensions) +
                       "&huId=" + encodeURIComponent(Date.now().toString(16) + Math.random().toString(16).slice(2)) +
                       "&variables=" + encodeURIComponent(variables);

          var headers = buildHeaders(token);
          headers["Content-Type"] = "application/graphql";

          return httpFetch(GRAPHQL_URL + "?" + params, {
            headers: headers
          });
        })
        .then(function(res) {
          if (!res.ok) return [];
          return res.json().then(function(body) {
            var panels = (body.data && body.data.panels) || [];
            var games = [];
            for (var i = 0; i < panels.length; i++) {
              var panel = panels[i];
              if (panel.apps && panel.apps.items) {
                var items = panel.apps.items;
                for (var j = 0; j < items.length; j++) {
                  games.push(items[j]);
                }
              }
            }
            return games;
          });
        })
        .catch(function() {
          return [];
        });
    },

    fetchStorePanels: function() { return Promise.resolve([]); },
    fetchMainGames: function() { return Promise.resolve([]); },
    fetchPublicGames: function() { return Promise.resolve([]); },
    resolveLaunchAppId: function() { return Promise.resolve(null); },
    resolveStoreUrl: function() { return Promise.resolve(null); },

    // GFN CloudMatch Session Lifecycle
    createSession: function(input) {
      var base = input.providerStreamingBaseUrl || "https://prod.cloudmatchbeta.nvidiagrid.net/";
      var cleanUrl = base.replace(/\/+$/, "");
      var body = {
        appId: input.appId,
        appStore: input.appStore,
        billingMode: input.billingMode || 1,
        cmsSignature: input.cmsSignature,
        playSource: input.playSource || "PLAYSOURCE_LIBRARY",
        targetGpuType: input.targetGpuType || 0,
        userAgreedToLStore: !!input.userAgreedToLStore
      };

      return httpFetch(cleanUrl + "/v2/session", {
        method: "POST",
        headers: buildHeaders(input.token),
        body: JSON.stringify(body)
      })
        .then(function(res) {
          if (!res.ok) {
            return res.text().then(function(errText) {
              throw new Error("Session creation failed: " + errText);
            });
          }
          return res.json();
        });
    },

    pollSession: function(input) {
      var base = input.providerStreamingBaseUrl || "https://prod.cloudmatchbeta.nvidiagrid.net/";
      var cleanUrl = base.replace(/\/+$/, "");
      return httpFetch(cleanUrl + "/v2/session/" + input.sessionId, {
        headers: buildHeaders(input.token)
      })
        .then(function(res) {
          if (!res.ok) throw new Error("Session polling failed");
          return res.json();
        });
    },

    reportSessionAd: function(input) {
      var base = input.providerStreamingBaseUrl || "https://prod.cloudmatchbeta.nvidiagrid.net/";
      var cleanUrl = base.replace(/\/+$/, "");
      return httpFetch(cleanUrl + "/v2/session/" + input.sessionId + "/adreport", {
        method: "POST",
        headers: buildHeaders(input.token),
        body: JSON.stringify({ adState: input.adState })
      })
        .then(function(res) {
          if (!res.ok) throw new Error("Ad reporting failed");
          return res.json();
        });
    },

    stopSession: function(input) {
      var base = input.providerStreamingBaseUrl || "https://prod.cloudmatchbeta.nvidiagrid.net/";
      var cleanUrl = base.replace(/\/+$/, "");
      return httpFetch(cleanUrl + "/v2/session/" + input.sessionId, {
        method: "DELETE",
        headers: buildHeaders(input.token)
      }).then(function() {
        return;
      });
    },

    getActiveSessions: function(token, streamingBaseUrl) {
      var base = streamingBaseUrl || "https://prod.cloudmatchbeta.nvidiagrid.net/";
      var cleanUrl = base.replace(/\/+$/, "");
      return httpFetch(cleanUrl + "/v2/session", {
        headers: buildHeaders(token)
      })
        .then(function(res) {
          if (!res.ok) return [];
          return res.json().then(function(data) {
            return data.sessionList || [];
          });
        })
        .catch(function() {
          return [];
        });
    },

    claimSession: function(input) {
      var base = input.providerStreamingBaseUrl || "https://prod.cloudmatchbeta.nvidiagrid.net/";
      var cleanUrl = base.replace(/\/+$/, "");
      return httpFetch(cleanUrl + "/v2/session/" + input.sessionId + "/claim", {
        method: "POST",
        headers: buildHeaders(input.token)
      })
        .then(function(res) {
          if (!res.ok) throw new Error("Session claim failed");
          return res.json();
        });
    },

    showSessionConflictDialog: function() {
      return Promise.resolve("terminate");
    },

    // Browser WebRTC Signaling via WebSocket
    connectSignaling: function(input) {
      if (api._signaling) {
        api._signaling.disconnect();
      }
      
      var peerName = "peer-" + Math.floor(Math.random() * 10000000000);
      var server = input.signalingServer.indexOf(":") >= 0 ? input.signalingServer : input.signalingServer + ":443";
      var url = "wss://" + server + "/nvst/sign_in?peer_id=" + peerName + "&version=2";
      var protocol = "x-nv-sessionid." + input.sessionId;

      var ws = new WebSocket(url, protocol);
      var ackCounter = 0;
      var heartbeatTimer = null;

      var client = {
        ws: ws,
        disconnect: function() {
          if (heartbeatTimer) {
            clearInterval(heartbeatTimer);
          }
          ws.close();
        },
        sendJson: function(payload) {
          if (ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify(payload));
          }
        }
      };

      ws.onopen = function() {
        ackCounter++;
        client.sendJson({
          ackid: ackCounter,
          peer_info: {
            browser: "Chrome",
            browserVersion: "131",
            connected: true,
            id: 2,
            name: peerName,
            peerRole: 0,
            resolution: "1920x1080",
            version: 2
          }
        });
        heartbeatTimer = setInterval(function() {
          client.sendJson({ hb: 1 });
        }, 5000);
        api._emitSignaling({ type: "connected" });
      };

      ws.onclose = function(e) {
        if (heartbeatTimer) {
          clearInterval(heartbeatTimer);
        }
        api._emitSignaling({ type: "disconnected", reason: e.reason || "socket closed" });
      };

      ws.onerror = function() {
        api._emitSignaling({ type: "error", message: "Signaling connection error" });
      };

      ws.onmessage = function(e) {
        var msg;
        try {
          msg = JSON.parse(e.data);
        } catch (err) {
          return;
        }

        if (msg.ackid && msg.peer_info && msg.peer_info.id !== 2) {
          client.sendJson({ ack: msg.ackid });
        }

        if (msg.hb) {
          client.sendJson({ hb: 1 });
          return;
        }

        if (msg.peer_msg && msg.peer_msg.msg) {
          var payload;
          try {
            payload = JSON.parse(msg.peer_msg.msg);
          } catch (err) {
            return;
          }

          if (payload.type === "offer" && payload.sdp) {
            api._emitSignaling({ type: "offer", sdp: payload.sdp });
          } else if (payload.candidate) {
            api._emitSignaling({
              type: "remote-ice",
              candidate: {
                candidate: payload.candidate,
                sdpMid: payload.sdpMid,
                sdpMLineIndex: payload.sdpMLineIndex
              }
            });
          }
        }
      };

      api._signaling = client;
      return Promise.resolve();
    },

    disconnectSignaling: function() {
      if (api._signaling) {
        api._signaling.disconnect();
        api._signaling = null;
      }
      return Promise.resolve();
    },

    sendAnswer: function(input) {
      if (api._signaling) {
        var answer = {
          type: "answer",
          sdp: input.sdp
        };
        if (input.nvstSdp) {
          answer.nvstSdp = input.nvstSdp;
        }
        api._signaling.sendJson({
          peer_msg: { from: 2, to: 1, msg: JSON.stringify(answer) },
          ackid: 1
        });
      }
      return Promise.resolve();
    },

    sendIceCandidate: function(input) {
      if (api._signaling) {
        api._signaling.sendJson({
          peer_msg: {
            from: 2,
            to: 1,
            msg: JSON.stringify({
              candidate: input.candidate,
              sdpMid: input.sdpMid,
              sdpMLineIndex: input.sdpMLineIndex
            })
          },
          ackid: 2
        });
      }
      return Promise.resolve();
    },

    _signalingListeners: [],
    onSignalingEvent: function(listener) {
      api._signalingListeners.push(listener);
      return function() {
        var index = api._signalingListeners.indexOf(listener);
        if (index >= 0) {
          api._signalingListeners.splice(index, 1);
        }
      };
    },
    _emitSignaling: function(event) {
      var listeners = api._signalingListeners;
      for (var i = 0; i < listeners.length; i++) {
        try {
          listeners[i](event);
        } catch (e) {
          console.error(e);
        }
      }
    },

    // UI/Platform APIs
    readClipboardText: function() {
      if (navigator.clipboard && typeof navigator.clipboard.readText === "function") {
        return navigator.clipboard.readText();
      }
      return Promise.resolve("");
    },
    onToggleFullscreen: function(cb) { return function() {}; },
    onTriggerScreenshot: function(cb) { return function() {}; },
    onExternalEscape: function(cb) { return function() {}; },
    getMicrophonePermission: function() { return Promise.resolve({ granted: false }); },
    notifyPointerLockChange: function() {},
    updateNativeShortcuts: function() {},
    fetchSubscriptionInfo: function() { return Promise.resolve(null); },
    getNativeCloudGsyncCapabilities: function() { return Promise.resolve({ supported: false }); },
    getNativeStreamerStatus: function() { return Promise.resolve({ detected: false, gstreamerAvailable: false }); },
    
    setFullscreen: function(v) {
      if (v) {
        if (document.documentElement.requestFullscreen) {
          var p1 = document.documentElement.requestFullscreen();
          if (p1 && typeof p1["catch"] === "function") {
            p1["catch"](function() {});
          }
        }
      } else {
        if (document.exitFullscreen) {
          var p2 = document.exitFullscreen();
          if (p2 && typeof p2["catch"] === "function") {
            p2["catch"](function() {});
          }
        }
      }
      return Promise.resolve();
    },
    toggleFullscreen: function() {
      if (!document.fullscreenElement) {
        if (document.documentElement.requestFullscreen) {
          var p3 = document.documentElement.requestFullscreen();
          if (p3 && typeof p3["catch"] === "function") {
            p3["catch"](function() {});
          }
        }
      } else {
        if (document.exitFullscreen) {
          var p4 = document.exitFullscreen();
          if (p4 && typeof p4["catch"] === "function") {
            p4["catch"](function() {});
          }
        }
      }
      return Promise.resolve();
    },
    togglePointerLock: function() { return Promise.resolve(); },
    
    // Direct Launch & Release Highlights (no-op)
    onDirectLaunchRequest: function(cb) { return function() {}; },
    getPendingDirectLaunchRequest: function() { return Promise.resolve(null); },
    onReleaseHighlightsShow: function(cb) { return function() {}; },
    clearDiscordActivity: function() { return Promise.resolve(); },
    quitApp: function() {
      window.close();
      return Promise.resolve();
    },
    fetchSubscription: function(input) {
      return Promise.resolve({
        membershipTier: "PREMIUM",
        allottedHours: 100,
        remainingHours: 100,
        isUnlimited: true,
        entitledResolutions: [
          { width: 1920, height: 1080, fps: 60 },
          { width: 3840, height: 2160, fps: 60 }
        ]
      });
    }
  };

  window.openNow = api;
})();
