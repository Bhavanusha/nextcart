
"use client";

import { useState, type FormEvent } from "react";
import { adminRequest, Message, Panel, readImage, Thumb, useAdminData } from "../ui";

type Preferences = { flatRate: number | null; freeShippingMinimum: number | null; cashOnDelivery: boolean; orderAlerts: boolean; lowStockAlerts: boolean };
type Settings = { storeName: string; contactEmail: string; contactPhone: string | null; addressLine1: string | null; addressLine2: string | null;
  city: string | null; stateOrProvince: string | null; postalCode: string | null; country: string | null; currencyCode: string;
  taxRate: number; timezone: string; logoUrl: string | null; preferences: Preferences };
const tabs = ["Store Settings", "Shipping", "Payment", "Notifications"] as const;
const currencies = { USD: "USD ($)", LKR: "LKR (Rs)", INR: "INR (₹)", EUR: "EUR (€)", GBP: "GBP (£)", CAD: "CAD ($)", AUD: "AUD ($)" };
const timezones = { "America/Los_Angeles": "Pacific Time", "America/Denver": "Mountain Time", "America/Chicago": "Central Time",
  "America/New_York": "Eastern Time", UTC: "UTC", "Europe/London": "London", "Asia/Colombo": "Sri Lanka", "Asia/Kolkata": "India" };

export default function SettingsPage() {
  const source = useAdminData<Settings>("/settings");
  if (source.error) return <main className="admin-content"><Message error={source.error} /></main>;
  if (!source.data) return <main className="admin-content admin-empty">Loading store settings…</main>;
  return <SettingsEditor initial={source.data} />;
}

function SettingsEditor({ initial }: { initial: Settings }) {
  const [saved, setSaved] = useState(initial);
  const [form, setForm] = useState(initial);
  const [tab, setTab] = useState<(typeof tabs)[number]>("Store Settings");
  const [logoData, setLogoData] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [reading, setReading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const update = <K extends keyof Settings>(key: K, value: Settings[K]) => setForm(current => ({ ...current, [key]: value }));
  const setPreference = <K extends keyof Preferences>(key: K, value: Preferences[K]) =>
    setForm(current => ({ ...current, preferences: { ...current.preferences, [key]: value } }));
  const logo = logoData ?? form.logoUrl;
  const currencyOptions = { [form.currencyCode]: form.currencyCode, ...currencies };
  const timezoneOptions = { ...timezones, ...(form.timezone in timezones ? {} : { [form.timezone]: form.timezone }) };

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError("");
    setSuccess("");
    try {
      const response = await adminRequest<Settings>("/settings", { method: "PUT", body: JSON.stringify({ ...form, logoData }) });
      setForm(response);
      setSaved(response);
      setLogoData(undefined);
      window.dispatchEvent(new Event("haven-settings-changed"));
      setSuccess("Settings saved.");
    } catch (failure) { setError((failure as Error).message); }
    finally { setSaving(false); }
  }

  async function pickLogo(file?: File) {
    if (!file || saving) return;
    setReading(true);
    setError("");
    try { setLogoData(await readImage(file)); }
    catch (failure) { setError((failure as Error).message); }
    finally { setReading(false); }
  }

  function reset() {
    setForm(saved);
    setLogoData(undefined);
    setError("");
    setSuccess("");
  }

  return <main className="admin-content">
    <div className="admin-tabs settings-tabs" aria-label="Settings sections">{tabs.map(value => <button key={value} aria-pressed={tab === value} onClick={() => setTab(value)}>{value}</button>)}</div>
    <Message error={error} success={success} />
    <form className="admin-split settings-form" onSubmit={submit}>
      <div className="admin-stack">
        {tab === "Store Settings" && <>
          <Panel title="Company Information"><div className="admin-fields">
            <div className="fields-two">
              <label>Store Name<input required maxLength={150} value={form.storeName} onChange={event => update("storeName", event.target.value)} /></label>
              <label>Store Contact Email<input required type="email" maxLength={320} value={form.contactEmail} onChange={event => update("contactEmail", event.target.value)} /></label>
            </div>
            <label>Phone Number<input type="tel" maxLength={30} value={form.contactPhone ?? ""} onChange={event => update("contactPhone", event.target.value)} /></label>
            <label>Street Address<input maxLength={255} value={form.addressLine1 ?? ""} onChange={event => update("addressLine1", event.target.value)} /></label>
            <div className="fields-three">
              <label>City<input maxLength={100} value={form.city ?? ""} onChange={event => update("city", event.target.value)} /></label>
              <label>State / Province<input maxLength={100} value={form.stateOrProvince ?? ""} onChange={event => update("stateOrProvince", event.target.value)} /></label>
              <label>ZIP / Postal Code<input maxLength={20} value={form.postalCode ?? ""} onChange={event => update("postalCode", event.target.value)} /></label>
            </div>
            <label>Country<input list="settings-countries" maxLength={100} value={form.country ?? ""} onChange={event => update("country", event.target.value)} />
              <datalist id="settings-countries">{["United States", "Sri Lanka", "India", "United Kingdom", "Canada", "Australia"].map(country => <option key={country} value={country} />)}</datalist></label>
          </div></Panel>
          <Panel title="General Localization"><div className="admin-fields fields-two">
            <label>Base Currency<select value={form.currencyCode} onChange={event => update("currencyCode", event.target.value)}>
              {Object.entries(currencyOptions).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
            <label>Timezone<select value={form.timezone} onChange={event => update("timezone", event.target.value)}>
              {Object.entries(timezoneOptions).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
          </div></Panel>
        </>}
        {tab === "Shipping" && <Panel title="Shipping Settings"><div className="admin-fields fields-two">
          <label>Flat Shipping Rate ({form.currencyCode})<input type="number" min="0" max="9999999999.99" step=".01" value={form.preferences.flatRate ?? ""}
            onChange={event => setPreference("flatRate", event.target.value === "" ? null : Number(event.target.value))} /></label>
          <label>Free Shipping Minimum ({form.currencyCode})<input type="number" min="0" max="9999999999.99" step=".01" value={form.preferences.freeShippingMinimum ?? ""}
            onChange={event => setPreference("freeShippingMinimum", event.target.value === "" ? null : Number(event.target.value))} /></label>
        </div></Panel>}
        {tab === "Payment" && <Panel title="Payment Settings"><div className="admin-fields">
          <label>Tax Rate (%)<input required type="number" min="0" max="100" step=".01" value={form.taxRate} onChange={event => update("taxRate", Number(event.target.value))} /></label>
          <label className="inline-field"><input type="checkbox" checked={form.preferences.cashOnDelivery} onChange={event => setPreference("cashOnDelivery", event.target.checked)} /> Enable cash on delivery</label>
        </div></Panel>}
        {tab === "Notifications" && <Panel title="Admin Notifications"><div className="admin-fields">
          <label className="inline-field"><input type="checkbox" checked={form.preferences.orderAlerts} onChange={event => setPreference("orderAlerts", event.target.checked)} /> Pending order alerts</label>
          <label className="inline-field"><input type="checkbox" checked={form.preferences.lowStockAlerts} onChange={event => setPreference("lowStockAlerts", event.target.checked)} /> Low-stock alerts</label>
        </div></Panel>}
      </div>
      <div className="admin-stack">
        {tab === "Store Settings" && <Panel title="Store Logo"><div className="logo-settings">
          {logo ? <Thumb src={logo} label="Store logo" size={100} /> : <span className="logo-preview" aria-label="Default store logo">{form.storeName.charAt(0) || "H"}</span>}
          <div className="admin-actions">
            <label className="admin-secondary-button file-button">{reading ? "Reading…" : "Change Logo"}<input type="file" accept="image/png,image/jpeg" disabled={reading || saving}
              onChange={event => { void pickLogo(event.target.files?.[0]); event.target.value = ""; }} /></label>
            <button type="button" className="admin-secondary-button danger-text" disabled={!logo || reading || saving} onClick={() => setLogoData("")}>Remove</button>
          </div>
          <small>PNG or JPG. Recommended size 512 × 512 px.</small>
        </div></Panel>}
        <div className="settings-actions"><button className="admin-action-button" disabled={saving || reading}>{saving ? "Saving…" : "Save Changes"}</button>
          <button className="admin-secondary-button" type="button" disabled={saving || reading} onClick={reset}>Cancel</button></div>
      </div>
    </form>
  </main>;
}
