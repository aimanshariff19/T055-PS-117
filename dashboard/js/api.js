const API_BASE = "/api";

export async function request(path, options = {}) {
  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...options,
      headers: {
        Accept: "application/json",
        ...(options.body ? { "Content-Type": "application/json" } : {}),
        ...options.headers,
      },
    });
  } catch (error) {
    throw new Error("Cannot reach the PS-117 API. Check that the local dashboard server is running.");
  }

  const contentType = response.headers.get("content-type") || "";
  const payload = contentType.includes("application/json") ? await response.json() : null;
  if (!response.ok) {
    throw new Error(payload?.error?.message || `Request failed (${response.status}).`);
  }
  return payload;
}

export const getHealth = () => request("/health");
export const getSummary = () => request("/dashboard/summary");
export const getApplications = () => request("/applications");
export const getAppointments = () => request("/appointments");
export const getAllocationResults = () => request("/allocation-results");
export const getDocuments = () => request("/documents");
export const getCentres = () => request("/centres");
export const getPriorities = () => request("/priorities");
export const getBilling = () => request("/billing");
export const getReports = () => request("/reports");
export const getSettings = () => request("/settings");

export const processApplications = (asOf) =>
  request("/process", {
    method: "POST",
    body: JSON.stringify(asOf ? { asOf } : {}),
  });
