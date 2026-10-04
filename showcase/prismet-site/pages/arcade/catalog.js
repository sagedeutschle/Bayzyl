// Names, categories and tile artwork follow the co-developed native Prismet catalog.
export const categories = ['Daily', 'Puzzles', 'Board', 'Cards', 'Workshop', 'Lenses'];
const entries = [
  ['wordle','Wordgame','Daily','wordle','Six guesses. One five-letter word.'],
  ['rubiks-cube',"Rubik’s Cube",'Puzzles','rubiks','Turn the faces. Find the pattern.'],
  ['2048','2048','Puzzles','2048','Merge a little. Think a move ahead.'],
  ['lights-out','Lights Out','Puzzles','lightsout','One press changes the neighborhood.'],
  ['minesweeper','Minesweeper','Puzzles','minesweeper','Every number is a clue.'],
  ['snake','Snake','Puzzles','snake','A familiar rhythm. One more bite.'],
  ['sudoku','Sudoku','Puzzles','sudoku','Nine rows, nine columns, one solution.'],
  ['sliding-15','Sliding-15','Puzzles','sliding','Make room for everything to fall into place.'],
  ['nonogram','Nonogram','Puzzles','nonogram','Read the clues. Reveal the picture.'],
  ['chess','Chess','Board','chess','A classic with room for your next idea.'],
  ['reversi','Reversi','Board','reversi','The edge can change everything.'],
  ['connect-four','Connect Four','Board','connectfour','Four in a row. No second chances.'],
  ['checkers','Checkers','Board','checkers','Find the jump. Make your king.'],
  ['gomoku','Gomoku','Board','gomoku','Five stones, one unbroken line.'],
  ['sea-battle','Sea Battle','Board','seabattle','Search the grid. Find the fleet.'],
  ['catan','Catan','Board',null,'Build a settlement. Make a trade.'],
  ['solitaire','Solitaire','Cards','solitaire','A quiet table and a fresh deal.'],
  ['crazy-8','Crazy 8','Cards','crazyeight','Match a suit. Change the direction of play.'],
  ['spider','Spider','Cards','spider','Build a sequence. Clear the table.'],
  ['brick-bench','Brick Bench','Workshop','brickbench','Build with parts, colors and layers.'],
  ['debt-clock','Debt Clock','Lenses','debtclock','Look closer at the public numbers.','/debt'],
  ['steam-rewind','Steam Rewind','Lenses','steamrewind','See your library through another lens.','/steam'],
];
export const catalog = entries.map(([id,title,category,art,description,href]) => ({ id,title,category,art: art ? `/arcade/art/${art}.webp` : null,description,href:href || `/arcade/${id}`,portable:['2048','minesweeper','lights-out'].includes(id) }));
export const byID = Object.assign(Object.create(null), Object.fromEntries(catalog.map((entry)=>[entry.id,entry])));
