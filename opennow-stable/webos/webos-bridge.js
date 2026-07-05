(function() {
  if (typeof window === 'undefined') return;

  // Check if window.openNow is already defined
  if (window.openNow) {
    console.log("[webOS Bridge] window.openNow is already defined, skipping shim.");
    return;
  }

  console.log("[webOS Bridge] Injecting webOS GFN shim...");

  const LCARS_CLIENT_ID = "ec7e38d4-03af-4b58-b131-cfb0495903ab";
  const GFN_CLIENT_VERSION = "2.0.80.173";
  const GRAPHQL_URL = "https://games.geforce.com/graphql";

  // Base client headers builder
  function buildHeaders(token) {
    return {
      "Accept": "application/json, text/plain, */*",
      "Content-Type": "application/json",
      "nv-client-id": LCARS_CLIENT_ID,
      "nv-client-type": "NATIVE",
      "nv-client-version": GFN_CLIENT_VERSION,
      "nv-client-streamer": "NVIDIA-CLASSIC",
      "nv-device-os": "LINUX",
      "nv-device-type": "DESKTOP",
      ...(token ? { "Authorization": `GFNJWT ${token}` } : {})
    };
  }

  // Settings management
  const defaultSettings = {
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
    const saved = localStorage.getItem("opennow_settings");
    return saved ? { ...defaultSettings, ...JSON.parse(saved) } : defaultSettings;
  }

  // Device Login State cache
  const deviceLoginAttempts = new Map();
  const pendingSessions = new Map();

  const api = {
    // Settings
    getSettings: () => Promise.resolve(getSettingsInternal()),
    setSetting: (key, value) => {
      const settings = getSettingsInternal();
      settings[key] = value;
      localStorage.setItem("opennow_settings", JSON.stringify(settings));
      return Promise.resolve();
    },
    resetSettings: () => {
      localStorage.removeItem("opennow_settings");
      return Promise.resolve(defaultSettings);
    },

    // Accounts
    getSavedAccounts: () => {
      const val = localStorage.getItem("opennow_saved_accounts");
      return Promise.resolve(val ? JSON.parse(val) : []);
    },
    getAuthSession: () => {
      const val = localStorage.getItem("opennow_auth_session");
      return Promise.resolve({
        session: val ? JSON.parse(val) : null,
        refresh: { outcome: "not_attempted" }
      });
    },
    switchAccount: (userId) => {
      const val = localStorage.getItem("opennow_saved_accounts");
      const accounts = val ? JSON.parse(val) : [];
      const acc = accounts.find(a => a.user.userId === userId);
      if (acc) {
        localStorage.setItem("opennow_auth_session", JSON.stringify(acc));
        return Promise.resolve(acc);
      }
      return Promise.reject(new Error("Account not found"));
    },
    removeAccount: (userId) => {
      const val = localStorage.getItem("opennow_saved_accounts");
      const accounts = val ? JSON.parse(val) : [];
      const filtered = accounts.filter(a => a.user.userId !== userId);
      localStorage.setItem("opennow_saved_accounts", JSON.stringify(filtered));

      const active = localStorage.getItem("opennow_auth_session");
      if (active) {
        const activeSession = JSON.parse(active);
        if (activeSession.user.userId === userId) {
          localStorage.removeItem("opennow_auth_session");
        }
      }
      return Promise.resolve();
    },
    logout: () => {
      localStorage.removeItem("opennow_auth_session");
      return Promise.resolve();
    },
    logoutAll: () => {
      localStorage.removeItem("opennow_auth_session");
      localStorage.setItem("opennow_saved_accounts", "[]");
      return Promise.resolve();
    },

    // Auth Providers / Regions
    getLoginProviders: async () => {
      try {
        const res = await fetch("https://pcs.geforcenow.com/v1/serviceUrls");
        if (!res.ok) throw new Error("Failed to fetch service URLs");
        const data = await res.json();
        const endpoints = data.gfnServiceInfo?.gfnServiceEndpoints || [];
        return endpoints.map(entry => ({
          idpId: entry.idpId,
          code: entry.loginProviderCode,
          displayName: entry.loginProviderCode === "BPC" ? "bro.game" : entry.loginProviderDisplayName,
          streamingServiceUrl: entry.streamingServiceUrl,
          priority: entry.loginProviderPriority || 0
        })).sort((a, b) => a.priority - b.priority);
      } catch (e) {
        console.warn("[webOS Bridge] Error loading login providers:", e);
        return [{ idpId: "NVIDIA", code: "NV", displayName: "GeForce NOW", streamingServiceUrl: "https://prod.cloudmatchbeta.nvidiagrid.net/", priority: 1 }];
      }
    },

    getRegions: async (input) => {
      const baseUrl = input?.providerStreamingBaseUrl || "https://prod.cloudmatchbeta.nvidiagrid.net/";
      try {
        const res = await fetch(`${baseUrl.replace(/\/+$/, "")}/v2/serverInfo`, {
          headers: buildHeaders(input?.token)
        });
        if (!res.ok) return [];
        const data = await res.json();
        return (data.zoneList || []).map(zone => ({
          name: zone.zoneName,
          url: zone.zoneUrl
        }));
      } catch (e) {
        console.warn("[webOS Bridge] Error loading regions:", e);
        return [];
      }
    },

    // Device Authorization OAuth Flow (QR/PIN Login)
    startDeviceLogin: async (input) => {
      const providers = await api.getLoginProviders();
      const provider = providers.find(p => p.idpId === input.providerIdpId) || providers[0];
      const deviceId = crypto.randomUUID();
      const body = new URLSearchParams({
        client_id: LCARS_CLIENT_ID,
        scope: "openid offline_access credential",
        device_id: deviceId,
        display_name: "OpenNOW webOS TV",
        idp_id: provider.idpId
      });

      const res = await fetch("https://login.nvidia.com/device/authorize", {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
          "x-device-id": deviceId,
          "nv-client-id": LCARS_CLIENT_ID,
          "nv-client-streamer": "WEBRTC",
          "nv-client-type": "BROWSER"
        },
        body
      });

      if (!res.ok) {
        throw new Error("Failed to start device authorization");
      }

      const challenge = await res.json();
      const attemptId = crypto.randomUUID().replace(/-/g, "");
      deviceLoginAttempts.set(attemptId, {
        provider,
        deviceCode: challenge.device_code,
        expiresAt: Date.now() + (challenge.expires_in * 1000),
        deviceId
      });

      return {
        attemptId,
        userCode: challenge.user_code,
        verificationUrl: challenge.verification_uri,
        verificationUrlWithCode: challenge.verification_uri_complete,
        expiresIn: challenge.expires_in,
        interval: challenge.interval,
        deviceCode: challenge.device_code
      };
    },

    pollDeviceLogin: async (input) => {
      const attempt = deviceLoginAttempts.get(input.attemptId);
      if (!attempt) return { status: "expired", error: "Device login expired" };
      if (Date.now() >= attempt.expiresAt) {
        deviceLoginAttempts.delete(input.attemptId);
        return { status: "expired", error: "Device login expired" };
      }

      const body = new URLSearchParams({
        grant_type: "urn:ietf:params:oauth:grant-type:device_code",
        device_code: attempt.deviceCode,
        client_id: LCARS_CLIENT_ID
      });

      const res = await fetch("https://login.nvidia.com/token", {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
          "x-device-id": attempt.deviceId,
          "nv-client-id": LCARS_CLIENT_ID
        },
        body
      });

      const data = await res.json();
      if (!res.ok) {
        if (data.error === "authorization_pending") {
          return { status: "pending" };
        }
        return { status: "error", error: data.error_description || "Login failed" };
      }

      // Parse user info from ID Token
      const token = data.id_token || data.access_token;
      let user = { userId: "unknown", displayName: "User", email: "", avatarUrl: "", membershipTier: "FREE" };
      try {
        const payload = JSON.parse(atob(token.split(".")[1]));
        user = {
          userId: payload.sub || "unknown",
          displayName: payload.preferred_username || payload.email?.split("@")[0] || "User",
          email: payload.email || "",
          avatarUrl: payload.picture || "",
          membershipTier: payload.gfn_tier || "FREE"
        };
      } catch (e) {
        console.error("[webOS Bridge] Error parsing JWT", e);
      }

      const tokens = {
        accessToken: data.access_token,
        refreshToken: data.refresh_token,
        idToken: data.id_token,
        expiresAt: Date.now() + (data.expires_in * 1000),
        authClientId: LCARS_CLIENT_ID,
        clientToken: data.client_token
      };

      const session = {
        provider: attempt.provider,
        tokens,
        user
      };

      pendingSessions.set(input.attemptId, session);
      return { status: "authorized" };
    },

    completeDeviceLogin: (input) => {
      const session = pendingSessions.get(input.attemptId);
      if (!session) return Promise.reject(new Error("No authorized session found"));
      
      pendingSessions.delete(input.attemptId);
      deviceLoginAttempts.delete(input.attemptId);

      // Save to accounts
      const val = localStorage.getItem("opennow_saved_accounts");
      const accounts = val ? JSON.parse(val) : [];
      const existingIdx = accounts.findIndex(a => a.user.userId === session.user.userId);
      if (existingIdx >= 0) {
        accounts[existingIdx] = session;
      } else {
        accounts.push(session);
      }
      localStorage.setItem("opennow_saved_accounts", JSON.stringify(accounts));
      localStorage.setItem("opennow_auth_session", JSON.stringify(session));

      return Promise.resolve(session);
    },

    cancelDeviceLogin: (input) => {
      deviceLoginAttempts.delete(input.attemptId);
      pendingSessions.delete(input.attemptId);
      return Promise.resolve();
    },

    login: () => Promise.reject(new Error("Standard login not supported on webOS TV. Please use QR Code / Device Login.")),

    // Catalog & Games (GraphQL client-side requests)
    browseCatalog: async (input) => {
      const token = input.token;
      const base = input.providerStreamingBaseUrl || "https://prod.cloudmatchbeta.nvidiagrid.net/";
      const serverInfoRes = await fetch(`${base.replace(/\/+$/, "")}/v2/serverInfo`, {
        headers: buildHeaders(token)
      });
      const serverInfo = await serverInfoRes.json();
      const vpcId = serverInfo.vpcId || "default";

      const variables = {
        vpcId,
        locale: "en_US",
        sortString: "itemMetadata.relevance:DESC,sortName:ASC",
        fetchCount: input.fetchCount || 60,
        cursor: "",
        filters: {}
      };
      if (input.searchQuery) {
        variables.searchString = input.searchQuery;
      }

      const appFields = `
        numberReturned
        numberSupported
        pageInfo { hasNextPage endCursor totalCount }
        items {
          id
          title
          images { KEY_ART KEY_IMAGE GAME_BOX_ART TV_BANNER HERO_IMAGE MARQUEE_HERO_IMAGE FEATURE_IMAGE GAME_LOGO SCREENSHOTS }
          variants {
            id
            appStore
            storeUrl
            supportedControls
            gfn {
              status
              library { status selected }
            }
          }
          gfn {
            playabilityState
            minimumMembershipTierLabel
          }
          itemMetadata { campaignIds }
        }
      `;

      const query = input.searchQuery
        ? `query GetSearchFilterResults($vpcId: String!, $locale: String!, $sortString: String!, $fetchCount: Int!, $cursor: String!, $searchString: String!, $filters: AppFilterFields!) {
            apps(vpcId: $vpcId, language: $locale, orderBy: $sortString, first: $fetchCount, after: $cursor, searchQuery: $searchString, filters: $filters) { ${appFields} }
          }`
        : `query GetFilterBrowseResults($vpcId: String!, $locale: String!, $sortString: String!, $fetchCount: Int!, $cursor: String!, $filters: AppFilterFields!) {
            apps(vpcId: $vpcId, language: $locale, orderBy: $sortString, first: $fetchCount, after: $cursor, filters: $filters) { ${appFields} }
          }`;

      const res = await fetch(GRAPHQL_URL, {
        method: "POST",
        headers: buildHeaders(token),
        body: JSON.stringify({ query, variables })
      });

      if (!res.ok) throw new Error("Catalog query failed");
      const body = await res.json();
      const appsData = body.data?.apps || { items: [], pageInfo: { hasNextPage: false, totalCount: 0 } };
      
      return {
        games: appsData.items || [],
        hasNextPage: appsData.pageInfo?.hasNextPage || false,
        totalCount: appsData.pageInfo?.totalCount || 0
      };
    },

    fetchFeaturedGames: async (input) => {
      const token = input.token;
      const base = input.providerStreamingBaseUrl || "https://prod.cloudmatchbeta.nvidiagrid.net/";
      const serverInfoRes = await fetch(`${base.replace(/\/+$/, "")}/v2/serverInfo`, {
        headers: buildHeaders(token)
      });
      const serverInfo = await serverInfoRes.json();
      const vpcId = serverInfo.vpcId || "default";

      const variables = JSON.stringify({
        vpcId,
        locale: "en_US",
        panelNames: ["MAIN"]
      });
      const extensions = JSON.stringify({
        persistedQuery: {
          sha256Hash: "f8e26265a5db5c20e1334a6872cf04b6e3970507697f6ae55a6ddefa5420daf0"
        }
      });
      const params = new URLSearchParams({
        requestType: "panels/MainV2",
        extensions,
        huId: `${Date.now().toString(16)}${Math.random().toString(16).slice(2)}`,
        variables
      });

      const res = await fetch(`${GRAPHQL_URL}?${params.toString()}`, {
        headers: {
          ...buildHeaders(token),
          "Content-Type": "application/graphql"
        }
      });
      if (!res.ok) return [];
      const body = await res.json();
      
      const panels = body.data?.panels || [];
      const games = [];
      for (const panel of panels) {
        if (panel.apps?.items) {
          games.push(...panel.apps.items);
        }
      }
      return games;
    },

    fetchLibraryGames: async (input) => {
      const token = input.token;
      const base = input.providerStreamingBaseUrl || "https://prod.cloudmatchbeta.nvidiagrid.net/";
      const serverInfoRes = await fetch(`${base.replace(/\/+$/, "")}/v2/serverInfo`, {
        headers: buildHeaders(token)
      });
      const serverInfo = await serverInfoRes.json();
      const vpcId = serverInfo.vpcId || "default";

      const variables = JSON.stringify({
        vpcId,
        locale: "en_US",
        panelNames: ["LIBRARY"]
      });
      const extensions = JSON.stringify({
        persistedQuery: {
          sha256Hash: "039e8c0d553972975485fee56e59f2549d2fdb518e247a42ab5022056a74406f"
        }
      });
      const params = new URLSearchParams({
        requestType: "panels/Library",
        extensions,
        huId: `${Date.now().toString(16)}${Math.random().toString(16).slice(2)}`,
        variables
      });

      const res = await fetch(`${GRAPHQL_URL}?${params.toString()}`, {
        headers: {
          ...buildHeaders(token),
          "Content-Type": "application/graphql"
        }
      });
      if (!res.ok) return [];
      const body = await res.json();
      
      const panels = body.data?.panels || [];
      const games = [];
      for (const panel of panels) {
        if (panel.apps?.items) {
          games.push(...panel.apps.items);
        }
      }
      return games;
    },

    fetchStorePanels: async () => [],
    fetchMainGames: async () => [],
    fetchPublicGames: async () => [],
    resolveLaunchAppId: async () => null,
    resolveStoreUrl: async () => null,

    // GFN CloudMatch Session Lifecycle
    createSession: async (input) => {
      const base = input.providerStreamingBaseUrl || "https://prod.cloudmatchbeta.nvidiagrid.net/";
      const body = {
        appId: input.appId,
        appStore: input.appStore,
        billingMode: input.billingMode || 1,
        cmsSignature: input.cmsSignature,
        playSource: input.playSource || "PLAYSOURCE_LIBRARY",
        targetGpuType: input.targetGpuType || 0,
        userAgreedToLStore: input.userAgreedToLStore || false
      };

      const res = await fetch(`${base.replace(/\/+$/, "")}/v2/session`, {
        method: "POST",
        headers: buildHeaders(input.token),
        body: JSON.stringify(body)
      });

      if (!res.ok) {
        const errText = await res.text();
        throw new Error(`Session creation failed: ${errText}`);
      }
      return res.json();
    },

    pollSession: async (input) => {
      const base = input.providerStreamingBaseUrl || "https://prod.cloudmatchbeta.nvidiagrid.net/";
      const res = await fetch(`${base.replace(/\/+$/, "")}/v2/session/${input.sessionId}`, {
        headers: buildHeaders(input.token)
      });
      if (!res.ok) throw new Error("Session polling failed");
      return res.json();
    },

    reportSessionAd: async (input) => {
      const base = input.providerStreamingBaseUrl || "https://prod.cloudmatchbeta.nvidiagrid.net/";
      const res = await fetch(`${base.replace(/\/+$/, "")}/v2/session/${input.sessionId}/adreport`, {
        method: "POST",
        headers: buildHeaders(input.token),
        body: JSON.stringify({ adState: input.adState })
      });
      if (!res.ok) throw new Error("Ad reporting failed");
      return res.json();
    },

    stopSession: async (input) => {
      const base = input.providerStreamingBaseUrl || "https://prod.cloudmatchbeta.nvidiagrid.net/";
      await fetch(`${base.replace(/\/+$/, "")}/v2/session/${input.sessionId}`, {
        method: "DELETE",
        headers: buildHeaders(input.token)
      });
    },

    getActiveSessions: async (token, streamingBaseUrl) => {
      const base = streamingBaseUrl || "https://prod.cloudmatchbeta.nvidiagrid.net/";
      try {
        const res = await fetch(`${base.replace(/\/+$/, "")}/v2/session`, {
          headers: buildHeaders(token)
        });
        if (!res.ok) return [];
        const data = await res.json();
        return data.sessionList || [];
      } catch (e) {
        return [];
      }
    },

    claimSession: async (input) => {
      const base = input.providerStreamingBaseUrl || "https://prod.cloudmatchbeta.nvidiagrid.net/";
      const res = await fetch(`${base.replace(/\/+$/, "")}/v2/session/${input.sessionId}/claim`, {
        method: "POST",
        headers: buildHeaders(input.token)
      });
      if (!res.ok) throw new Error("Session claim failed");
      return res.json();
    },

    showSessionConflictDialog: () => Promise.resolve("terminate"),

    // Browser WebRTC Signaling via WebSocket
    connectSignaling: async (input) => {
      if (api._signaling) api._signaling.disconnect();
      
      const peerName = `peer-${Math.floor(Math.random() * 10000000000)}`;
      const server = input.signalingServer.includes(":") ? input.signalingServer : `${input.signalingServer}:443`;
      const url = `wss://${server}/nvst/sign_in?peer_id=${peerName}&version=2`;
      const protocol = `x-nv-sessionid.${input.sessionId}`;

      const ws = new WebSocket(url, protocol);
      let ackCounter = 0;
      let heartbeatTimer = null;

      const client = {
        ws,
        disconnect: () => {
          clearInterval(heartbeatTimer);
          ws.close();
        },
        sendJson: (payload) => {
          if (ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify(payload));
          }
        }
      };

      ws.onopen = () => {
        client.sendJson({
          ackid: ++ackCounter,
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
        heartbeatTimer = setInterval(() => client.sendJson({ hb: 1 }), 5000);
        api._emitSignaling({ type: "connected" });
      };

      ws.onclose = (e) => {
        clearInterval(heartbeatTimer);
        api._emitSignaling({ type: "disconnected", reason: e.reason || "socket closed" });
      };

      ws.onerror = () => {
        api._emitSignaling({ type: "error", message: "Signaling connection error" });
      };

      ws.onmessage = (e) => {
        let msg;
        try {
          msg = JSON.parse(e.data);
        } catch {
          return;
        }

        if (msg.ackid && msg.peer_info?.id !== 2) {
          client.sendJson({ ack: msg.ackid });
        }

        if (msg.hb) {
          client.sendJson({ hb: 1 });
          return;
        }

        if (msg.peer_msg?.msg) {
          let payload;
          try {
            payload = JSON.parse(msg.peer_msg.msg);
          } catch {
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
    },

    disconnectSignaling: () => {
      if (api._signaling) {
        api._signaling.disconnect();
        api._signaling = null;
      }
      return Promise.resolve();
    },

    sendAnswer: (input) => {
      if (api._signaling) {
        const answer = {
          type: "answer",
          sdp: input.sdp,
          ...(input.nvstSdp ? { nvstSdp: input.nvstSdp } : {})
        };
        api._signaling.sendJson({
          peer_msg: { from: 2, to: 1, msg: JSON.stringify(answer) },
          ackid: 1
        });
      }
      return Promise.resolve();
    },

    sendIceCandidate: (input) => {
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

    _signalingListeners: new Set(),
    onSignalingEvent: (listener) => {
      api._signalingListeners.add(listener);
      return () => api._signalingListeners.delete(listener);
    },
    _emitSignaling: (event) => {
      for (const listener of api._signalingListeners) listener(event);
    },

    // UI/Platform APIs
    readClipboardText: () => navigator.clipboard ? navigator.clipboard.readText() : Promise.resolve(""),
    onToggleFullscreen: (cb) => { return () => {}; },
    onTriggerScreenshot: (cb) => { return () => {}; },
    onExternalEscape: (cb) => { return () => {}; },
    getMicrophonePermission: () => Promise.resolve({ granted: false }),
    notifyPointerLockChange: () => {},
    updateNativeShortcuts: () => {},
    fetchSubscriptionInfo: () => Promise.resolve(null),
    getNativeCloudGsyncCapabilities: () => Promise.resolve({ supported: false }),
    getNativeStreamerStatus: () => Promise.resolve({ detected: false, gstreamerAvailable: false }),
    
    setFullscreen: (v) => {
      if (v) {
        document.documentElement.requestFullscreen().catch(() => {});
      } else {
        document.exitFullscreen().catch(() => {});
      }
      return Promise.resolve();
    },
    toggleFullscreen: () => {
      if (!document.fullscreenElement) {
        document.documentElement.requestFullscreen().catch(() => {});
      } else {
        document.exitFullscreen().catch(() => {});
      }
      return Promise.resolve();
    },
    togglePointerLock: () => Promise.resolve(),
    
    // Direct Launch & Release Highlights (no-op)
    onDirectLaunchRequest: (cb) => { return () => {}; },
    getPendingDirectLaunchRequest: () => Promise.resolve(null),
    onReleaseHighlightsShow: (cb) => { return () => {}; },
    clearDiscordActivity: () => Promise.resolve(),
    quitApp: () => {
      window.close();
      return Promise.resolve();
    },
    fetchSubscription: (input) => Promise.resolve({
      membershipTier: "PREMIUM",
      allottedHours: 100,
      remainingHours: 100,
      isUnlimited: true,
      entitledResolutions: [
        { width: 1920, height: 1080, fps: 60 },
        { width: 3840, height: 2160, fps: 60 }
      ]
    })
  };

  window.openNow = api;
})();
