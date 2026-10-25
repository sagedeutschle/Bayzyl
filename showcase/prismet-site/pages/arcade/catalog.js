// Names, categories, tile artwork and accents follow the co-developed native Prismet catalog (GameCard.all).
export const categories = ['Daily', 'Puzzles', 'Board', 'Cards', 'Workshop', 'Lenses'];
const entries = [
  ['wordle','Wordgame','Daily','wordle','Six guesses. One five-letter word.','#669959'],
  ['rubiks-cube',"Rubik’s Cube",'Puzzles','rubiks','Turn the faces. Find the pattern.','#75579E'],
  ['2048','2048','Puzzles','2048','Merge a little. Think a move ahead.','#DB8C2E'],
  ['lights-out','Lights Out','Puzzles','lightsout','One press changes the neighborhood.','#D9B333'],
  ['minesweeper','Minesweeper','Puzzles','minesweeper','Every number is a clue.','#4D8C6B'],
  ['snake','Snake','Puzzles','snake','A familiar rhythm. One more bite.','#3D75A8'],
  ['sudoku','Sudoku','Puzzles','sudoku','Nine rows, nine columns, one solution.','#B35959'],
  ['sliding-15','Sliding-15','Puzzles','sliding','Make room for everything to fall into place.','#4D80B3'],
  ['nonogram','Nonogram','Puzzles','nonogram','Read the clues. Reveal the picture.','#8C598C'],
  ['chess','Chess','Board','chess','A classic with room for your next idea.','#8C6638'],
  ['reversi','Reversi','Board','reversi','The edge can change everything.','#338C66'],
  ['connect-four','Connect Four','Board','connectfour','Four in a row. No second chances.','#D98C33'],
  ['checkers','Checkers','Board','checkers','Find the jump. Make your king.','#B34D40'],
  ['gomoku','Gomoku','Board','gomoku','Five stones, one unbroken line.','#6B7A57'],
  ['sea-battle','Sea Battle','Board','seabattle','Search the grid. Find the fleet.','#296BAD'],
  ['catan','Settler Scramble','Board','catan','Build a settlement. Make a trade.','#CC853D'],
  ['solitaire','Solitaire','Cards','solitaire','A quiet table and a fresh deal.','#33734D'],
  ['crazy-8','Crazy 8','Cards','crazyeight','Match a suit. Change the direction of play.','#99476B'],
  ['spider','Spider','Cards','spider','Build a sequence. Clear the table.','#6B3D2E'],
  ['brick-bench','Brick Bench','Workshop','brickbench','Build with parts, colors and layers.','#CC3333'],
  ['debt-clock','Debt Clock','Lenses','debtclock','Look closer at the public numbers.','#339E6B','/debt'],
  ['steam-rewind','Steam Rewind','Lenses','steamrewind','See your library through another lens.','#426BB8','/steam'],
  ['uncle-scam','Uncle Scam','Lenses','unclescam','See where your tax dollar goes.','#B3261F','/scam'],
];
const aliases = {'catan':['catan','settlers','settler scramble'],'wordle':['word game'],'sea-battle':['battleship'],'connect-four':['connect 4','connect4'],'crazy-8':['crazy eights','crazy eight'],'sliding-15':['15 puzzle','fifteen puzzle'],'reversi':['othello'],'nonogram':['picross'],'rubiks-cube':['rubik cube','rubiks cube'],'uncle-scam':['tax','taxes','tax receipt']};
export const catalog = entries.map(([id,title,category,art,description,accent,href]) => ({ id,title,category,aliases:aliases[id]||[],art: art ? `/arcade/art/${art}.svg` : null,description,accent,href:href || `/arcade/${id}`,portable:['2048','minesweeper','lights-out'].includes(id) }));
export const byID = Object.assign(Object.create(null), Object.fromEntries(catalog.map((entry)=>[entry.id,entry])));

const normalized = value => String(value).toLowerCase().replace(/[’']/g,'').replace(/[-_]/g,' ').trim();
export function matchesCatalog(entry, query) {
  const haystack=normalized([entry.id,entry.title,entry.category,entry.description,...(entry.aliases||[])].join(' '));
  return normalized(query).split(/\s+/).every(word=>haystack.includes(word));
}
export const collectionCount = (query, count) => normalized(query) ? `${count} of ${catalog.length} in the collection` : `${catalog.length} in the collection`;
