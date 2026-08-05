import type { Metadata } from "next";
import "./globals.css";
export const metadata:Metadata={title:"MedRAG",description:"Secure clinical data summarization"};
export default function RootLayout({children}:{children:React.ReactNode}){return <html lang="en"><body>{children}</body></html>}
