import { buildGfnGraphQlHeaders } from "./clientHeaders";
import { fetchWithOptionalProxy } from "./proxyFetch";

export const LCARS_GRAPHQL_URL = "https://apps.gxn.nvidia.com/graphql";
export const LCARS_CDN_GRAPHQL_URL = "https://games.geforce.com/graphql";

export type LcarsQueryName =
  | "Marquee"
  | "Main"
  | "LibrarySection"
  | "LibrarySectionWithTime"
  | "FilterGroupAndSortOrderDefinitions"
  | "AppsWithSearch"
  | "AppsWithoutSearch"
  | "AppDataForAppId"
  | "AddOwnedVariant";

interface LcarsQueryDefinition {
  requestType?: string;
  sha256Hash?: string;
  query?: string;
}

const GAME_SECTION_QUERY = `
query GetGameSection($vpcId: String!, $locale: String!, $panelNames: [String]!) {
  panels(vpcId: $vpcId, language: $locale, names: $panelNames) {
    id
    name
    sections {
      id
      title
      renderDirectives
      seeMoreInfo {
        filterTileId
        title
        filterIds
        minTiles
        sortOrderId
      }
      items {
        __typename
        ...filterFields
        ...minimalGameFields
        ...marketingFields
      }
    }
  }
}

fragment filterFields on FilterItem {
  id
  title
  image
  filterIds
  sortOrderId
}

fragment minimalGameFields on GameItem {
  app {
    id
    images {
      TV_BANNER
      HERO_IMAGE
    }
    title
    itemMetadata {
      campaignIds
    }
    variants {
      id
      shortName
      appStore
      supportedControls
      minimumSizeInBytes
      gfn {
        library {
          status
          selected
          playStatus
          installed
          subscription
        }
        status
        stateDetails {
          ... on VariantGfnAutoPatchingMetadata {
            subType
            endTime
          }
          ... on VariantGfnManualPatchingMetadata {
            subType
            endTime
          }
          ... on VariantGfnMaintenanceMetadata {
            subType
          }
        }
      }
    }
    gfn {
      playabilityState
      minimumMembershipTierLabel
      catalogSkuStrings {
        SKU_BASED_TAG
      }
      playType
    }
  }
}

fragment marketingFields on MarketingItem {
  id
  title
  subTitle
  body
  images {
    MARQUEE_HERO_IMAGE
    HERO_IMAGE
  }
  action {
    uri
    label
    infoText
  }
  schedule {
    startTime
    endTime
  }
}`;

const MARQUEE_QUERY = `
query GetMarquee($vpcId: String!, $locale: String!, $panelNames: [String]!) {
  panels(vpcId: $vpcId, language: $locale, names: $panelNames) {
    id
    name
    sections {
      id
      title
      items {
        __typename
        ...marketingFields
        ...marqueeGameFields
      }
    }
  }
}

fragment marketingFields on MarketingItem {
  id
  title
  images {
    MARQUEE_HERO_IMAGE
    HERO_IMAGE
  }
  body
  action {
    uri
    label
    infoText
  }
  schedule {
    startTime
    endTime
  }
}

fragment marqueeGameFields on GameItem {
  app {
    id
    title
    publisherName
    contentRatings {
      categoryKey
      contentDescriptorKeys
      interactiveElementKeys
      type
    }
    marqueeScrimPrimaryRGB {
      r
      g
      b
    }
    images {
      GAME_LOGO
      MARQUEE_HERO_IMAGE
      HERO_IMAGE
    }
    itemMetadata {
      campaignIds
    }
    variants {
      id
      appStore
      supportedControls
    }
  }
}`;

const LIBRARY_SECTION_WITH_TIME_QUERY = `
query GetGameSection($vpcId: String!, $locale: String!, $panelNames: [String]!) {
  panels(vpcId: $vpcId, language: $locale, names: $panelNames) {
    id
    name
    sections {
      id
      title
      renderDirectives
      seeMoreInfo {
        filterTileId
        title
        filterIds
        minTiles
        sortOrderId
      }
      items {
        __typename
        ...filterFields
        ...minimalGameFields
      }
    }
  }
}

fragment filterFields on FilterItem {
  id
  title
  image
  filterIds
  sortOrderId
}

fragment minimalGameFields on GameItem {
  app {
    id
    images {
      TV_BANNER
      HERO_IMAGE
      KEY_ART
    }
    title
    itemMetadata {
      campaignIds
    }
    variants {
      id
      shortName
      appStore
      publisherName
      supportedControls
      gfn {
        library {
          status
          selected
          playStatus
          lastPlayedDate
          subscription
        }
        status
        stateDetails {
          ... on VariantGfnAutoPatchingMetadata {
            subType
            endTime
          }
          ... on VariantGfnManualPatchingMetadata {
            subType
            endTime
          }
          ... on VariantGfnMaintenanceMetadata {
            subType
          }
        }
      }
      contentRatings {
        categoryKey
        type
      }
    }
    gfn {
      playabilityState
      minimumMembershipTierLabel
      catalogSkuStrings {
        SKU_BASED_TAG
      }
    }
  }
}`;

const FILTER_GROUP_AND_SORT_QUERY = `query GetFilterGroupAndSortOrderDefinitions($locale: String!) {
  filterGroupDefinitions(language: $locale) {
    id
    label
    filters {
      id
      label
      filters
    }
  }
  sortOrderDefinitions(language: $locale) {
    id
    label
    orderBy
  }
}`;

const APP_DATA_FOR_APP_ID_QUERY = `
query GetAppDataQueryForAppId($vpcId: String!, $locale: String!, $appIds: [String]!) {
  apps(vpcId: $vpcId, language: $locale, appIds: $appIds) {
    items {
      contentRatings {
        categoryKey
        contentDescriptorKeys
        interactiveElementKeys
        type
      }
      developerName
      id
      genres
      images {
        GAME_BOX_ART
        GAME_LOGO
        HERO_IMAGE
        SCREENSHOTS
        TV_BANNER
        KEY_ART
      }
      nvidiaTech {
        PHOTO_MODE
        FREESTYLE
        HIGHLIGHTS
      }
      title
      longDescription
      shortDescription
      maxLocalPlayers
      maxOnlinePlayers
      supportedControls
      publisherName
      itemMetadata {
        campaignIds
      }
      variants {
        appStore
        id
        shortName
        supportedControls
        storeUrl
        publisherName
        developerName
        subscriptions
        minimumSizeInBytes
        cloudSaveSupported
        gfn {
          library {
            installed
            status
            selected
            playStatus
            subscription
            lastPlayedDate
          }
          status
          features {
            ...feature
          }
        }
      }
      gfn {
        playabilityState
        minimumMembershipTierLabel
        catalogSkuStrings {
          SKU_BASED_TAG
          SKU_BASED_PLAYABILITY_TEXT
          SKU_BASED_UNPLAYABLE_DIALOG_HEADER
          SKU_BASED_UNPLAYABLE_DIALOG_BODY_UPGRADE
          SKU_BASED_UNPLAYABLE_DIALOG_BODY_UPGRADE_ECOMM_RESTRICTED
        }
        playType
      }
    }
  }
}

fragment feature on GfnSubscriptionFeature {
  __typename
  ... on GfnSubscriptionFeatureValue {
    key
    value
  }
  ... on GfnSubscriptionFeatureValueList {
    key
    values
  }
}`;

const ADD_OWNED_VARIANT_MUTATION = `mutation AddOwnedVariant($cmsId: String!, $locale: String!) {
  addOwnedVariant(language: $locale, variantId: $cmsId) {
    app {
      id
    }
  }
}`;

export const LCARS_QUERY_DEFINITIONS: Record<LcarsQueryName, LcarsQueryDefinition> = {
  Marquee: {
    requestType: "panels/Marquee",
    sha256Hash: "dd4bddfdef4707dfe340cc2040d6bb9c4c45f706976fca15b2ef33221c385d7f",
    query: MARQUEE_QUERY,
  },
  Main: {
    requestType: "panels/MainV2",
    sha256Hash: "46ec15f267a056e7d5e46e629efa929529e5e7542a4850faece90b9f8fa5f810",
    query: GAME_SECTION_QUERY,
  },
  LibrarySection: {
    requestType: "panels/Library",
    sha256Hash: "46ec15f267a056e7d5e46e629efa929529e5e7542a4850faece90b9f8fa5f810",
    query: GAME_SECTION_QUERY,
  },
  LibrarySectionWithTime: {
    requestType: "panels/Library",
    sha256Hash: "7f54d6bbbf3b1c09d0e5264dfa36f0f4aaf5e2678f2089f0cbf0d4dda18c3af9",
    query: LIBRARY_SECTION_WITH_TIME_QUERY,
  },
  FilterGroupAndSortOrderDefinitions: {
    requestType: "filterGroupAndSortOrderDefinitions",
    sha256Hash: "ef725de5e93b093de1ac7418fed0ffb4f6ae2b9c14f743ab274a791521488eb9",
    query: FILTER_GROUP_AND_SORT_QUERY,
  },
  AppsWithSearch: {
    requestType: "apps",
    sha256Hash: "ea1b5e417c95ceb5c7d6a65aa4613a417ed80b1a8d6a8c26b6953846da1fc513",
  },
  AppsWithoutSearch: {
    requestType: "apps",
    sha256Hash: "5ae1cfe2e04debdcd81279b5559313abab7d9cfa3ac9d9c048e969b3d445dcb9",
  },
  AppDataForAppId: {
    requestType: "appMetaData",
    sha256Hash: "cf8b620dfd03617017ba7c858cee65197e1ace5180e41be194b39227227ced63",
    query: APP_DATA_FOR_APP_ID_QUERY,
  },
  AddOwnedVariant: {
    sha256Hash: "02b373dd20366da6a7184c16a8a84505cc3d15e9f35788c0104c4d16456bcfaf",
    query: ADD_OWNED_VARIANT_MUTATION,
  },
};

export interface GraphQlErrorPayload {
  message?: string;
}

export interface GraphQlResponsePayload {
  errors?: GraphQlErrorPayload[];
}

interface FetchLcarsGraphQlOptions {
  context?: string;
  endpoint?: string;
  fallbackQuery?: string;
}

function randomHuId(): string {
  return `${Date.now().toString(16)}${Math.random().toString(16).slice(2)}`;
}

function getLcarsQueryDefinition(queryName: LcarsQueryName): LcarsQueryDefinition {
  return LCARS_QUERY_DEFINITIONS[queryName];
}

function buildPersistedQueryParams(
  definition: LcarsQueryDefinition,
  variables: Record<string, unknown>,
): URLSearchParams {
  const params = new URLSearchParams({
    extensions: JSON.stringify({
      persistedQuery: { sha256Hash: definition.sha256Hash },
    }),
    huId: randomHuId(),
    variables: JSON.stringify(variables),
  });

  if (definition.requestType) {
    params.set("requestType", definition.requestType);
  }

  return params;
}

export function throwGraphQlErrors(errors: GraphQlErrorPayload[] | undefined, context: string): void {
  if (errors?.length) {
    throw new Error(`${context}: ${errors.map((error) => error.message ?? "Unknown error").join(", ")}`);
  }
}

export async function fetchLcarsGraphQl<T extends GraphQlResponsePayload>(
  queryName: LcarsQueryName,
  variables: Record<string, unknown>,
  token?: string,
  proxyUrl?: string,
  options: FetchLcarsGraphQlOptions = {},
): Promise<T> {
  const definition = getLcarsQueryDefinition(queryName);
  if (!definition.sha256Hash) {
    throw new Error(`LCARS query '${queryName}' does not define a persisted-query hash`);
  }

  const endpoint = options.endpoint ?? LCARS_CDN_GRAPHQL_URL;
  const context = options.context ?? "GFN GraphQL failed";
  const query = options.fallbackQuery ?? definition.query;
  const params = buildPersistedQueryParams(definition, variables);
  const headers = {
    ...buildGfnGraphQlHeaders(token),
    "Content-Type": "application/graphql",
  };

  let response = await fetchWithOptionalProxy(`${endpoint}?${params.toString()}`, { headers }, proxyUrl);

  if (response.status === 400 && query) {
    params.set("query", query);
    response = await fetchWithOptionalProxy(`${endpoint}?${params.toString()}`, { headers }, proxyUrl);
  }

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`${context} (${response.status}): ${text.slice(0, 400)}`);
  }

  return (await response.json()) as T;
}

export async function postLcarsGraphQl<T extends GraphQlResponsePayload>(
  query: string,
  variables: Record<string, unknown>,
  token: string,
  proxyUrl?: string,
): Promise<T> {
  const response = await fetchWithOptionalProxy(LCARS_GRAPHQL_URL, {
    method: "POST",
    headers: buildGfnGraphQlHeaders(token),
    body: JSON.stringify({ query, variables }),
  }, proxyUrl);

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`GFN library mutation failed (${response.status}): ${text.slice(0, 400)}`);
  }

  const payload = (await response.json()) as T;
  throwGraphQlErrors(payload.errors, "GFN library mutation failed");
  return payload;
}

export async function postLcarsMutation<T extends GraphQlResponsePayload>(
  queryName: LcarsQueryName,
  variables: Record<string, unknown>,
  token: string,
  proxyUrl?: string,
): Promise<T> {
  const definition = getLcarsQueryDefinition(queryName);
  if (!definition.query) {
    throw new Error(`LCARS mutation '${queryName}' does not define a query`);
  }

  return postLcarsGraphQl<T>(definition.query, variables, token, proxyUrl);
}
