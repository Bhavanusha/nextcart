"use client";
import { Breadcrumb, Photo, useStore } from "../StoreShell";

export default function AboutPage() {
  const { settings } = useStore();
  return <main className="sf-wrap sf-page"><Breadcrumb><strong>Our Story</strong></Breadcrumb><section className="sf-hero">
    <div><p className="sf-upper sf-accent">Our Philosophy</p><h1>{settings?.content.storyTitle}</h1><p>{settings?.content.storyDescription}</p></div>
    <Photo className="sf-hero-image" src={settings?.content.storyImage}/>
  </section></main>;
}
