import { chromium } from 'playwright';

const baseUrl = process.env.ADMIN_URL || 'http://localhost:8081';
const username = process.env.ADMIN_USERNAME || 'admin';
const password = process.env.ADMIN_PASSWORD || 'change_me';
const searchTerm = process.env.TTS_BROWSER_SEARCH || 'Райкин';
const expectedRef = process.env.TICKETTOSHOW_REF_CODE || 'gAAAAABp_MhvvV3sf7YjfILZk4uXfZu_5pIozTIc0Z7JuqPs8ZRZ9yjuRI1bmS_wl9-RA_tUpj8OzLB-pyk-BSlPZ7qf5S93ZTjCfND_S2BWq_cD5_L4tak=';

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({
  viewport: { width: 390, height: 844 },
});
const page = await context.newPage();

const result = {
  baseUrl,
  searchTerm,
  openedUrl: null,
  selectedRecord: null,
  ticketUrl: null,
  checks: [],
};

function check(name, condition, value = undefined) {
  result.checks.push({ name, pass: Boolean(condition), value });
  if (!condition) {
    throw new Error(`${name} failed${value === undefined ? '' : `: ${value}`}`);
  }
}

try {
  await page.goto(baseUrl, { waitUntil: 'networkidle', timeout: 30000 });
  result.openedUrl = page.url();

  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Open admin' }).click();

  await page.getByText(/records$/).waitFor({ timeout: 30000 });
  check('admin loaded records table', await page.locator('.records-table').isVisible());

  const row = page.getByRole('row').filter({ hasText: searchTerm }).first();
  await row.waitFor({ timeout: 30000 });
  await row.click();
  result.selectedRecord = await row.innerText();

  check('selected row contains expected event', result.selectedRecord.includes(searchTerm), result.selectedRecord);

  const formText = await page.locator('.workspace-editor').innerText();
  check('editor shows Tickettoshow URL', formText.includes('tickettoshow.ru/concert'), formText);
  check('editor shows ref code', formText.includes(expectedRef) || formText.includes('ref='), formText);
  check('editor shows HTTPS image URL', formText.includes('https://moscowshow.com/'), formText);
  check('editor shows city Moscow', formText.includes('Москва'), formText);

  result.ticketUrl = await page.locator('.workspace-editor input').evaluateAll((inputs) => {
    const match = inputs
      .map((input) => input.value)
      .find((value) => value.includes('tickettoshow.ru/concert'));
    return match ?? null;
  });

  console.log(JSON.stringify(result, null, 2));
} finally {
  await browser.close();
}
