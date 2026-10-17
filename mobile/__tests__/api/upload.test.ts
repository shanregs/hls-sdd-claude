import { ApiError, NoConnectionError } from "../../src/api/errors";
import {
  fetchBinary,
  fileNameFromDisposition,
  registerClientHeadersProvider,
  setAuthHooks,
  upload,
} from "../../src/api/httpClient";
import { toBase64 } from "../../src/formats/base64";
import { setAccessToken } from "../../src/security/memoryToken";

/** Spec 033 T006: a multipart upload and a binary download go through the same client as every other call. */
type FetchMock = jest.Mock<Promise<Response>, [string, RequestInit]>;

let fetchMock: FetchMock;

beforeEach(() => {
  fetchMock = jest.fn();
  globalThis.fetch = fetchMock as unknown as typeof fetch;
  setAccessToken("access-1");
  setAuthHooks(null);
  jest.clearAllMocks();
});

const FILE = { uri: "file:///cache/check-in.jpg", name: "check-in.jpg", type: "image/jpeg" };
const headersOf = (call: number) => (fetchMock.mock.calls[call][1].headers ?? {}) as Record<string, string>;

describe("upload", () => {
  it("sends one file part under the field name, with the token and the provider headers, and no JSON content type", async () => {
    const unregister = registerClientHeadersProvider(async () => ({ "X-HLS-Location": "lat=1;lng=2;acc=3;ts=4" }));
    fetchMock.mockImplementation(async () => new Response(JSON.stringify({ id: "d1" }), { status: 201 }));

    const answer = await upload<{ id: string }>("/api/v1/x/photo", "file", FILE);
    unregister();

    expect(answer).toEqual({ id: "d1" });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain("/api/v1/x/photo");
    expect(init.method).toBe("POST");
    expect(init.body).toBeInstanceOf(FormData);
    const parts = (init.body as unknown as { getParts: () => { fieldName: string; name: string; type: string }[] }).getParts();
    expect(parts).toHaveLength(1);
    expect(parts[0]).toMatchObject({ fieldName: "file", name: "check-in.jpg", type: "image/jpeg" });
    expect(headersOf(0).Authorization).toBe("Bearer access-1");
    expect(headersOf(0)["X-HLS-Location"]).toBe("lat=1;lng=2;acc=3;ts=4");
    expect(headersOf(0)["Content-Type"]).toBeUndefined();
  });

  it("renews the session once after a 401 and sends the file again", async () => {
    setAuthHooks({
      renewAccess: async () => {
        setAccessToken("access-2");
        return true;
      },
    });
    fetchMock
      .mockResolvedValueOnce(new Response("", { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: "d1" }), { status: 201 }));

    await upload("/api/v1/x/photo", "file", FILE);

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(headersOf(1).Authorization).toBe("Bearer access-2");
  });

  it("turns the server's refusal into an error with its reason", async () => {
    fetchMock.mockImplementation(
      async () => new Response(JSON.stringify({ reason: "A photo must be a jpg or png file." }), { status: 400 }),
    );

    await expect(upload("/api/v1/x/photo", "file", FILE)).rejects.toMatchObject({
      status: 400,
      message: "A photo must be a jpg or png file.",
    });
    await expect(upload("/api/v1/x/photo", "file", FILE)).rejects.toBeInstanceOf(ApiError);
  });

  it("says no connection when the request cannot be made", async () => {
    fetchMock.mockRejectedValue(new TypeError("Network request failed"));

    await expect(upload("/api/v1/x/photo", "file", FILE)).rejects.toBeInstanceOf(NoConnectionError);
  });
});

describe("fetchBinary", () => {
  it("returns the bytes with the type, the size and the file name the server sent", async () => {
    fetchMock.mockResolvedValue(
      new Response(new Uint8Array([1, 2, 3, 4]) as unknown as BodyInit, {
        status: 200,
        headers: {
          "Content-Type": "image/png",
          "Content-Length": "4",
          "Content-Disposition": 'attachment; filename="front-gate.png"',
        },
      }),
    );

    const answer = await fetchBinary("/api/v1/x/photo");

    expect(Array.from(answer.bytes)).toEqual([1, 2, 3, 4]);
    expect(answer).toMatchObject({ contentType: "image/png", sizeBytes: 4, fileName: "front-gate.png" });
    expect(headersOf(0).Authorization).toBe("Bearer access-1");
  });

  it("uses the number of bytes received when the server sends no length, and reports a missing file name as null", async () => {
    fetchMock.mockResolvedValue(
      new Response(new Uint8Array([9, 9]) as unknown as BodyInit, { status: 200, headers: { "Content-Type": "image/jpeg" } }),
    );

    const answer = await fetchBinary("/api/v1/x/photo");

    expect(answer.sizeBytes).toBe(2);
    expect(answer.fileName).toBeNull();
  });

  it("turns a missing photo into an error with the server's reason", async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ reason: "There is no photo for this day." }), { status: 404 }));

    await expect(fetchBinary("/api/v1/x/photo")).rejects.toMatchObject({ status: 404, message: "There is no photo for this day." });
  });
});

describe("helpers", () => {
  it("reads a plain and an encoded file name from Content-Disposition", () => {
    expect(fileNameFromDisposition('attachment; filename="a b.jpg"')).toBe("a b.jpg");
    expect(fileNameFromDisposition("attachment; filename*=UTF-8''gate%20photo.png")).toBe("gate photo.png");
    expect(fileNameFromDisposition("attachment")).toBeNull();
    expect(fileNameFromDisposition(null)).toBeNull();
  });

  it("encodes bytes as base64, with padding", () => {
    expect(toBase64(new Uint8Array([]))).toBe("");
    expect(toBase64(new Uint8Array([77]))).toBe("TQ==");
    expect(toBase64(new Uint8Array([77, 97]))).toBe("TWE=");
    expect(toBase64(new Uint8Array([77, 97, 110]))).toBe("TWFu");
  });
});
