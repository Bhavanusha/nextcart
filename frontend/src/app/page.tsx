import StoreShell from "./(store)/StoreShell";
import Home from "./(store)/Home";
import "./(store)/store.css";

export default function Page() {
  return <StoreShell><Home/></StoreShell>;
}
