import {
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type AuthFetch,
  type PageOf,
} from "../common/masterDataApi";

export interface ZoneSummary {
  id: string;
  name: string;
  version: number;
  placeCount: number;
  schoolCount: number;
  /** Contributed by the organization module once Managers exist. */
  managerCount?: number;
}

export interface PlaceSummary {
  id: string;
  name: string;
  pinCode: string;
  zoneId: string;
  zoneName: string;
}

export function listZones(
  authFetch: AuthFetch,
  query: string,
  page: number,
  size: number,
): Promise<ApiResult<PageOf<ZoneSummary>>> {
  return getJson(
    authFetch,
    `/api/v1/zones?${queryString({ query: query.trim(), page, size })}`,
    "Could not load zones.",
  );
}

export function createZone(
  authFetch: AuthFetch,
  name: string,
): Promise<ApiResult<ZoneSummary>> {
  return sendJson(authFetch, "POST", "/api/v1/zones", { name });
}

export function renameZone(
  authFetch: AuthFetch,
  zone: ZoneSummary,
  name: string,
): Promise<ApiResult<ZoneSummary>> {
  return sendJson(authFetch, "PUT", `/api/v1/zones/${zone.id}`, {
    name,
    version: zone.version,
  });
}

export function deleteZone(
  authFetch: AuthFetch,
  zoneId: string,
): Promise<ApiResult> {
  return sendJson(
    authFetch,
    "DELETE",
    `/api/v1/zones/${zoneId}`,
    undefined,
    false,
  );
}

export function listPlaces(
  authFetch: AuthFetch,
  zoneId: string,
  query: string,
  page: number,
  size: number,
): Promise<ApiResult<PageOf<PlaceSummary>>> {
  return getJson(
    authFetch,
    `/api/v1/zones/${zoneId}/places?${queryString({ query: query.trim(), page, size })}`,
    "Could not load places.",
  );
}

export function lookupPlaces(
  authFetch: AuthFetch,
  pinCode: string,
  name: string,
): Promise<ApiResult<PlaceSummary[]>> {
  return getJson(
    authFetch,
    `/api/v1/places?${queryString({ pinCode: pinCode.trim(), name: name.trim() })}`,
    "Could not look up places.",
  );
}

export function addPlace(
  authFetch: AuthFetch,
  zoneId: string,
  name: string,
  pinCode: string,
): Promise<ApiResult<PlaceSummary>> {
  return sendJson(authFetch, "POST", `/api/v1/zones/${zoneId}/places`, {
    name,
    pinCode,
  });
}

export function editPlace(
  authFetch: AuthFetch,
  place: PlaceSummary,
  name: string,
  pinCode: string,
): Promise<ApiResult<PlaceSummary>> {
  return sendJson(authFetch, "PUT", `/api/v1/places/${place.id}`, {
    name,
    pinCode,
    zoneId: place.zoneId,
  });
}

export function deletePlace(
  authFetch: AuthFetch,
  placeId: string,
): Promise<ApiResult> {
  return sendJson(
    authFetch,
    "DELETE",
    `/api/v1/places/${placeId}`,
    undefined,
    false,
  );
}
