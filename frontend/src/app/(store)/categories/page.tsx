import { CategoryTiles } from "../Home";
import { Breadcrumb } from "../StoreShell";

export default function CategoriesPage() {
  return <main className="sf-wrap sf-page"><Breadcrumb><strong>Categories</strong></Breadcrumb><h1>Shop by Category</h1><CategoryTiles/></main>;
}
