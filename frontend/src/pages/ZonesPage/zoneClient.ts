// research.md §5 (specs/007-school-zone): reuses authClient's Bearer-token
// pattern (identity, spec 002) — no changes needed to Identity's/Organization's
// frontend code, just a new client for this page.

export interface ZoneView {
  id: string;
  name: string;
}

export interface CurrentSchoolZoneAssignment {
  id: string;
  zoneId: string;
  effectiveFrom: string;
}

export interface SchoolZoneAnswer {
  state: "CURRENT_ZONE" | "UNASSIGNED";
  zoneId?: string;
}

export interface PlaceView {
  id: string;
  zoneId: string;
  name: string;
  pincode: string;
}

async function request<T>(
  accessToken: string,
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await fetch(path, {
    ...init,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
      ...(init.headers ?? {}),
    },
  });
  if (!response.ok) {
    if (response.status === 409) {
      const body = await response
        .json()
        .catch(() => ({ message: "Conflict." }));
      throw new Error(
        body.message ??
          "This School's Zone assignment was already changed by someone else.",
      );
    }
    if (response.status === 404) {
      throw new Error("Not found.");
    }
    throw new Error(`Request failed: ${response.status}`);
  }
  return (await response.json()) as T;
}

export const zoneClient = {
  createZone(accessToken: string, name: string) {
    return request<ZoneView>(accessToken, "/api/v1/school/zones", {
      method: "POST",
      body: JSON.stringify({ name }),
    });
  },

  getZone(accessToken: string, zoneId: string) {
    return request<ZoneView>(accessToken, `/api/v1/school/zones/${zoneId}`);
  },

  getZoneSchools(accessToken: string, zoneId: string) {
    return request<string[]>(
      accessToken,
      `/api/v1/school/zones/${zoneId}/schools`,
    );
  },

  assignSchoolToZone(
    accessToken: string,
    schoolId: string,
    zoneId: string,
    endsAssignmentId?: string,
  ) {
    return request<CurrentSchoolZoneAssignment>(
      accessToken,
      "/api/v1/school/school-zone-assignments",
      {
        method: "POST",
        body: JSON.stringify({ schoolId, zoneId, endsAssignmentId }),
      },
    );
  },

  getSchoolZone(accessToken: string, schoolId: string) {
    return request<SchoolZoneAnswer>(
      accessToken,
      `/api/v1/school/schools/${schoolId}/zone`,
    );
  },

  addPlace(accessToken: string, zoneId: string, name: string, pincode: string) {
    return request<PlaceView>(accessToken, "/api/v1/school/places", {
      method: "POST",
      body: JSON.stringify({ zoneId, name, pincode }),
    });
  },

  findPlacesByPincode(accessToken: string, pincode: string) {
    return request<PlaceView[]>(
      accessToken,
      `/api/v1/school/places?pincode=${encodeURIComponent(pincode)}`,
    );
  },

  findPlacesByName(accessToken: string, name: string) {
    return request<PlaceView[]>(
      accessToken,
      `/api/v1/school/places?name=${encodeURIComponent(name)}`,
    );
  },

  getZonePlaces(accessToken: string, zoneId: string) {
    return request<PlaceView[]>(
      accessToken,
      `/api/v1/school/zones/${zoneId}/places`,
    );
  },
};
