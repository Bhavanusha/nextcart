"use client";
import { useEffect, useRef, useState } from "react";
import { AddressBlock, AddressFields, blankAddress } from "../../Commerce";
import { ErrorLine, Icon, useStore } from "../../StoreShell";
import { request, useData, type Address, type AddressInput } from "../../data";

export default function AddressesPage() {
  const { profile, settings, refreshProfile } = useStore();
  const source = useData<Address[]>("/addresses");
  const [selected,setSelected] = useState<Address|"new">();
  const [error,setError] = useState("");
  const [busy,setBusy] = useState(false);
  const [changed,setChanged] = useState<Address[]>();
  const addresses = changed ?? source.data ?? [];
  async function change(path: string,method: string) {
    setBusy(true); setError("");
    try { setChanged(await request<Address[]>("/addresses/"+path,{method})); refreshProfile(); }
    catch(failure) { setError((failure as Error).message); } finally { setBusy(false); }
  }
  return <>
    <div className="sf-spread sf-address-heading"><div><h1>My Addresses</h1><p>Manage your shipping addresses for faster checkout.</p></div>
      <button className="sf-btn" onClick={()=>setSelected("new")}>+ Add New Address</button></div>
    <ErrorLine error={error || source.error}/>
    <div className="sf-addresses">{addresses.map(address=><section className={"sf-panel "+(address.primary?"primary":"")} key={address.id}>
      <div className="sf-spread"><h3>{address.label}</h3>{address.primary && <span className="sf-badge default">Default</span>}</div>
      <AddressBlock address={address}/><p>{address.phone}</p>
      <div className="sf-actions sf-address-actions">
        <button className="sf-text-button sf-under" disabled={busy} onClick={()=>setSelected(address)}>Edit</button>
        <button className="sf-text-button sf-under" disabled={busy} onClick={()=>{if(confirm("Remove this address?")) void change(String(address.id),"DELETE");}}>Remove</button>
        {!address.primary && <button className="sf-text-button sf-accent" disabled={busy} onClick={()=>void change(address.id+"/default","PUT")}>Set as Default</button>}
      </div>
    </section>)}</div>
    {source.data && !addresses.length && <p className="sf-empty">You haven’t saved any addresses yet.</p>}
    {selected && <AddressDialog key={selected==="new"?"new":selected.id} row={selected} initial={selected==="new"?{
      ...blankAddress,name:profile?profile.firstName+" "+profile.lastName:"",phone:profile?.phone??"",country:settings?.country??""}:selected}
      close={()=>setSelected(undefined)} saved={rows=>{setChanged(rows);setSelected(undefined);refreshProfile();}}/>}
  </>;
}

function AddressDialog({ row,initial,close,saved }: { row: Address|"new"; initial: AddressInput; close: () => void; saved: (rows:Address[]) => void }) {
  const dialog=useRef<HTMLDialogElement>(null);
  const [form,setForm]=useState(initial);
  const [error,setError]=useState("");
  const [busy,setBusy]=useState(false);
  useEffect(()=>{dialog.current?.showModal();},[]);
  return <dialog className="sf-dialog sf-address-dialog" ref={dialog} aria-labelledby="address-title" onCancel={event=>{event.preventDefault();if(!busy)close();}}>
    <div className="sf-spread"><h2 id="address-title">{row==="new"?"Add New Address":"Edit Address"}</h2>
      <button className="sf-icon" type="button" aria-label="Close" disabled={busy} onClick={close}><Icon name="close"/></button></div>
    <form onSubmit={async event=>{
      event.preventDefault();setError("");setBusy(true);
      try { saved(await request<Address[]>("/addresses"+(row==="new"?"":"/"+row.id),{method:row==="new"?"POST":"PUT",body:JSON.stringify(form)})); }
      catch(failure) {setError((failure as Error).message);setBusy(false);}
    }}><AddressFields value={form} change={setForm} labeled/><ErrorLine error={error}/>
      <div className="sf-actions sf-end"><button className="sf-btn outline" type="button" disabled={busy} onClick={close}>Cancel</button><button className="sf-btn" disabled={busy}>Save Address</button></div></form>
  </dialog>;
}
