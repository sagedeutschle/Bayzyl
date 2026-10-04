// Public, framework-free data vocabulary shared by the service and dashboard.
export const GROUPS = [
  ['national', 'National Debt'], ['ratios', 'Per Citizen & Ratios'],
  ['treasury', 'Treasury & Reserves'], ['revenue', 'Revenue & Income'],
  ['spending', 'Spending & Deficit'], ['household', 'Consumer & Household Debt'],
  ['benefits', 'Benefits'], ['economy', 'Economy & Money'], ['people', 'Labor & People'],
].map(([id, label]) => ({ id, label }));

// Scale is applied once at ingestion. Every dollar series uses actual dollars.
// Frequency describes the observation period, not how often this page refreshes.
const definitions = [
  ['totalDebt','U.S. national debt','national','dollars','treasury-debt',1,'Daily',10,'Gross federal debt: public holdings plus intragovernmental holdings.'],
  ['debtHeldByPublic','Debt held by the public','national','dollars','treasury-debt',1,'Daily',10],
  ['intragovernmentalHoldings','Intragovernmental holdings','national','dollars','treasury-debt',1,'Daily',10],
  ['debtGrowthPerSecond','Average debt change / second','national','dollarsPerSecond',null,1,'Trailing 365-day change',10,'Estimated average change between the latest debt observation and the newest observation on or before 365 calendar days earlier, divided by the actual elapsed seconds. A shorter fallback is explicitly labeled when annual history is unavailable. Not a live spending rate.'],
  ['debtSubjectToLimit','Debt subject to limit','national','dollars','treasury-limit',1,'Daily',10,'Public and intragovernmental debt, less debt not subject to limit, plus other debt subject to limit. This is not the statutory ceiling.'],
  ['federalDebtFRED','Federal debt · quarterly','national','dollars','GFDEBTN',1e6,'Quarterly',200],
  ['foreignHeldFederalDebt','Foreign-held federal debt','national','dollars','FDHBFIN',1e9,'Quarterly',200],
  ['debtPerCitizen','Debt per citizen','ratios','dollarsPerPerson',null,1,'Mixed frequency',100,'Gross debt divided by the latest population observation available at each date; not a personal bill.'],
  ['debtToGDP','Gross debt / GDP','ratios','percent',null,1,'Mixed frequency',200,'Gross Treasury debt divided by nominal GDP at an annual rate; input periods differ.'],
  ['federalDebtToGDP','Federal debt / GDP · quarterly','ratios','percent','GFDEGDQ188S',1,'Quarterly',200],
  ['averageInterestRate','Average interest rate','ratios','percent','treasury-interest',1,'Monthly',100,'Average rate on total interest-bearing Treasury debt.'],
  ['treasuryGeneralAccount','Treasury General Account','treasury','dollars','treasury-cash',1,'Daily',10,'Daily Treasury Statement closing balance, reported in millions and normalized to dollars.'],
  ['goldReserveOunces','Gold reserve · fine troy ounces','treasury','fineTroyOunces','treasury-gold',1,'Monthly',100],
  ['goldReserveBookValue','Gold reserve · book value','treasury','dollars','treasury-gold',1,'Monthly',100,'Statutory book value, not current market value.'],
  ['fedBalanceSheetAssets','Federal Reserve assets','treasury','dollars','WALCL',1e6,'Weekly',25],
  ['federalReceipts','Federal receipts · annual rate','revenue','dollars','FGRECPT',1e9,'Quarterly · annual rate',200],
  ['monthlyReceipts','Monthly federal receipts','revenue','dollars','MTSR133FMS',1e6,'Monthly',100],
  ['receiptsPerCitizen','Receipts per citizen · annual rate','revenue','dollarsPerPerson',null,1,'Mixed frequency · annual rate',200],
  ['receiptsShareOfGDP','Federal receipts / GDP','revenue','percent','FYFRGDA188S',1,'Annual',800,'FRED ratio of federal receipts (FYFR) to annual GDP (GDPA); see source definition for the reporting periods.'],
  ['personalIncome','Personal income · annual rate','revenue','dollars','PI',1e9,'Monthly · annual rate',100],
  ['federalSpending','Federal spending · annual rate','spending','dollars','FGEXPND',1e9,'Quarterly · annual rate',200],
  ['monthlyOutlays','Monthly federal outlays','spending','dollars','MTSO133FMS',1e6,'Monthly',100],
  ['spendingPerCitizen','Spending per citizen · annual rate','spending','dollarsPerPerson',null,1,'Mixed frequency · annual rate',200],
  ['annualDeficit','Federal surplus / deficit · fiscal year','spending','dollars','FYFSD',1e6,'Annual · fiscal year',800,'Source convention: a negative value is a deficit; a positive value is a surplus.'],
  ['monthlyDeficit','Monthly surplus / deficit','spending','dollars','MTSDS133FMS',1e6,'Monthly',100,'Source convention: a negative value is a deficit; a positive value is a surplus.'],
  ['deficitPerCitizen','Deficit per citizen · annual rate','spending','dollarsPerPerson',null,1,'Mixed frequency · annual rate',200,'Federal spending minus receipts, divided by population. Positive means a deficit; negative means a surplus.'],
  ['netInterestOutlays','Net interest outlays · fiscal year','spending','dollars','FYOINT',1e6,'Annual · fiscal year',800],
  ['consumerCredit','Total consumer credit','household','dollars','TOTALSL',1e6,'Monthly',100],
  ['creditCardDebt','Revolving credit · card-debt proxy','household','dollars','REVOLSL',1e6,'Monthly',100,'Revolving consumer credit includes credit cards and other revolving loans; it is a proxy, not a card-only total.'],
  ['studentLoanDebt','Student loans · discontinued series','household','dollars','SLOAS',1e6,'Quarterly · discontinued',200,'FRED marks SLOAS discontinued. Historical observations only; not a current total.'],
  ['autoLoanDebt','Auto loans · discontinued series','household','dollars','MVLOAS',1e6,'Quarterly · discontinued',200,'FRED marks MVLOAS discontinued. Historical observations only; not a current total.'],
  ['mortgageDebt','Household residential mortgages','household','dollars','HHMSDODNS',1e6,'Quarterly',200,'Households and nonprofit organizations; one-to-four-family residential mortgages.'],
  ['socialSecurityBenefits','Social Security benefits · annual rate','benefits','dollars','W823RC1',1e9,'Monthly · annual rate',100],
  ['medicareBenefits','Medicare benefits · annual rate','benefits','dollars','W824RC1',1e9,'Monthly · annual rate',100],
  ['gdp','Gross domestic product · annual rate','economy','dollars','GDP',1e9,'Quarterly · annual rate',200],
  ['m2MoneyStock','M2 money stock','economy','dollars','M2SL',1e9,'Monthly',100],
  ['cpi','Consumer price index','economy','index','CUUR0000SA0',1,'Monthly',100,'All urban consumers, all items, not seasonally adjusted; 1982–84 = 100.'],
  ['laborForce','Civilian labor force','people','people','LNS11000000',1e3,'Monthly',100],
  ['employedWorkers','Employed people','people','people','LNS12000000',1e3,'Monthly',100],
  ['unemployedWorkers','Unemployed people','people','people','LNS13000000',1e3,'Monthly',100],
  ['notInLaborForce','Not in the labor force','people','people','LNS15000000',1e3,'Monthly',100],
  ['unemploymentRate','Unemployment rate','people','percent','LNS14000000',1,'Monthly',100],
  ['population','U.S. population','people','people','POPTHM',1e3,'Monthly',100,'BEA population includes resident population plus armed forces overseas. Monthly estimates are published through FRED.'],
];

export const METRICS = definitions.map(([id,label,group,unit,series,scale,frequency,maxAgeDays,note='']) => ({id,label,group,unit,series,scale,frequency,maxAgeDays,note}));
export const UNIT_LABELS = { dollars:'U.S. dollars', dollarsPerPerson:'Dollars per person', dollarsPerSecond:'Dollars per second', percent:'Percent', people:'People', index:'Index', fineTroyOunces:'Fine troy ounces' };
export function formatValue(value, unit, { compact = false } = {}) {
  if (!Number.isFinite(value)) return 'Unavailable';
  const options = { maximumFractionDigits: unit === 'percent' || unit === 'index' ? 2 : 0 };
  if (compact) Object.assign(options, {notation:'compact', maximumFractionDigits:2});
  let text = new Intl.NumberFormat('en-US', options).format(value);
  if (unit.startsWith('dollars')) text = '$' + text;
  if (unit === 'percent') text += '%';
  return text;
}

// A backward-only join: no future population observation is used for an older point.
export function atOrBefore(history, date) {
  let lo=0, hi=history.length-1, match=null;
  while(lo<=hi) { const mid=(lo+hi)>>1; if(history[mid].date<=date){match=history[mid];lo=mid+1;}else hi=mid-1; }
  return match;
}
export function perPersonHistory(history, population, maxAgeDays=100) {
  return history.flatMap(point => { const pop=atOrBefore(population,point.date); return pop?.value>0 && Date.parse(point.date)-Date.parse(pop.date)<=maxAgeDays*86400000 ? [{...point,value:point.value/pop.value,inputDates:{...point.inputDates,population:pop.date}}] : []; });
}
export function rangeHistory(history, range, endDate) {
  if(range==='all') return history;
  const end = Date.parse(endDate+'T00:00:00Z');
  const span = { '1y':365.25, '5y':365.25*5, '3m':92 }[range] ?? 365.25;
  return history.filter(p => Date.parse(p.date+'T00:00:00Z') >= end-span*86400000 && Date.parse(p.date+'T00:00:00Z') <= end);
}
export function csvForSeries(series) {
  const cell=v=>'"'+String(v??'').replaceAll('"','""')+'"';
  const rows=[['metric','date','value','unit','status','source_url','input_dates']];
  for(const metric of series) for(const point of metric.history) rows.push([metric.label,point.date,point.value,metric.unit,metric.status,metric.sourceUrl,JSON.stringify(point.inputDates||{})]);
  return rows.map(row=>row.map(cell).join(',')).join('\r\n');
}
