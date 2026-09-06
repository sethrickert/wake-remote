import {defineConfig} from 'vite';
import react from '@vitejs/plugin-react';
import {VitePWA} from 'vite-plugin-pwa';
export default defineConfig({plugins:[react(),VitePWA({registerType:'autoUpdate',includeAssets:['icon.png','apex-shield.webp'],manifest:{name:'Wake Remote',short_name:'Wake Remote',description:'Securely send Wake-on-LAN signals through your server.',theme_color:'#0C0C0C',background_color:'#0C0C0C',display:'standalone',icons:[{src:'icon-192.png',sizes:'192x192',type:'image/png'},{src:'icon-512.png',sizes:'512x512',type:'image/png'}]}})]});
