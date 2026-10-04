import './app-shell.js';
import { byID } from './catalog.js';
const id = location.pathname.split('/').filter(Boolean).at(-1);
if (byID[id]?.portable || id === 'game.html') await import('./arcade.js');
else await import('./extended.js');
