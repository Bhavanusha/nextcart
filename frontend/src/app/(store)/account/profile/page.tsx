"use client";
import { useState } from "react";
import { ErrorLine, useStore } from "../../StoreShell";
import { request, type Profile } from "../../data";
import { Avatar } from "../OrderViews";

export default function ProfilePage() {
  const { profile } = useStore();
  return <><h1>Profile Settings</h1><p>Update your personal details, email address, and password.</p>
    {profile && <Personal initial={profile}/>}<Password/></>;
}

function Personal({ initial }: { initial: Profile }) {
  const { refreshProfile }=useStore();
  const [form,setForm]=useState(initial);
  const [avatar,setAvatar]=useState<string>();
  const [error,setError]=useState("");
  const [success,setSuccess]=useState("");
  const [busy,setBusy]=useState(false);
  const [reading,setReading]=useState(false);
  const fields = [["firstName","First Name","text","given-name",100],["lastName","Last Name","text","family-name",100],
    ["email","Email Address","email","email",320],["phone","Phone Number","tel","tel",30]] as const;
  return <section className="sf-panel sf-account-section"><h2>Personal Information</h2>
    <form className="sf-fields" onSubmit={async event=>{
      event.preventDefault();setBusy(true);setError("");setSuccess("");
      try {
        const result=await request<Profile>("/account",{method:"PUT",body:JSON.stringify({
          firstName:form.firstName,lastName:form.lastName,email:form.email,phone:form.phone,avatar})});
        setForm(result);setAvatar(undefined);refreshProfile();setSuccess("Changes saved.");
      } catch(failure) {setError((failure as Error).message);} finally {setBusy(false);}
    }}>
      <div className="sf-actions sf-profile-photo"><Avatar profile={{...form,avatar:avatar??form.avatar}}/><div>
        <label className="sf-file sf-accent">Change Photo<input type="file" accept="image/jpeg,image/png,image/gif" disabled={busy||reading} onChange={event=>{
          const file=event.target.files?.[0];event.target.value="";
          if(!file) return; if(!["image/jpeg","image/png","image/gif"].includes(file.type)||file.size>2*1024*1024){setError("Choose JPG, PNG or GIF up to 2 MB.");return;}
          const reader=new FileReader();setReading(true);setError("");
          reader.onload=()=>{setAvatar(String(reader.result));setReading(false);};reader.onerror=()=>{setError("Could not read the image.");setReading(false);};reader.readAsDataURL(file);
        }}/></label><small>JPG, GIF or PNG. Max size of 2 MB.</small></div></div>
      <div className="sf-fields-two">{fields.map(([key,label,type,auto,max])=><label key={key}>{label}<input required={key!=="phone"} type={type}
        autoComplete={auto} maxLength={max} value={form[key]??""} onChange={event=>setForm({...form,[key]:event.target.value})}/></label>)}</div>
      <ErrorLine error={error}/>{success && <p className="sf-green" role="status">{success}</p>}
      <div><button className="sf-btn" disabled={busy||reading}>Save Changes</button></div>
    </form>
  </section>;
}

function Password() {
  const [current,setCurrent]=useState("");
  const [next,setNext]=useState("");
  const [confirm,setConfirm]=useState("");
  const [error,setError]=useState("");
  const [success,setSuccess]=useState("");
  const [busy,setBusy]=useState(false);
  return <section className="sf-panel sf-account-section"><h2>Change Password</h2><form className="sf-fields" onSubmit={async event=>{
    event.preventDefault();setError("");setSuccess("");
    if(next!==confirm){setError("New passwords do not match.");return;}
    setBusy(true);
    try {await request("/account/password",{method:"PUT",body:JSON.stringify({currentPassword:current,newPassword:next})});setCurrent("");setNext("");setConfirm("");setSuccess("Password updated.");}
    catch(failure){setError((failure as Error).message);}finally{setBusy(false);}
  }}>
    <label>Current Password<input type="password" autoComplete="current-password" required maxLength={72} value={current} onChange={event=>setCurrent(event.target.value)}/></label>
    <div className="sf-fields-two"><label>New Password<input type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={next} onChange={event=>setNext(event.target.value)}/></label>
      <label>Confirm New Password<input type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={confirm} onChange={event=>setConfirm(event.target.value)}/></label></div>
    <ErrorLine error={error}/>{success && <p className="sf-green" role="status">{success}</p>}<div><button className="sf-btn" disabled={busy}>Update Password</button></div>
  </form></section>;
}
