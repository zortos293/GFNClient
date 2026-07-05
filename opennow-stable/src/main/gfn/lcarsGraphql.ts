import { buildGfnGraphQlHeaders } from "./clientHeaders";
import { fetchWithOptionalProxy } from "./proxyFetch";

export const LCARS_GRAPHQL_URL = "https://apps.gxn.nvidia.com/graphql";

export interface GraphQlErrorPayload {
  message?: string;
}

export interface GraphQlResponsePayload {
  errors?: GraphQlErrorPayload[];
}

export function throwGraphQlErrors(errors: GraphQlErrorPayload[] | undefined, context: string): void {
  if (errors?.length) {
    throw new Error(`${context}: ${errors.map((error) => error.message ?? "Unknown error").join(", ")}`);
  }
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
