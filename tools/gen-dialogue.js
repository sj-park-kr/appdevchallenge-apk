// DIALOGUE.md의 대사 표를 app/src/main/assets/dialogue.tsv로 바꾼다.
// 사용: node tools/gen-dialogue.js [DIALOGUE.md 경로] [출력 경로]
// 열: id <TAB> 단계 <TAB> 조건(쉼표=AND, 세로선=OR) <TAB> 대사
const fs = require('fs');
const path = require('path');

const input = process.argv[2] || path.join(__dirname, '..', '..', 'appdevchallenge', 'DIALOGUE.md');
const output = process.argv[3] || path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'dialogue.tsv');

// 문서의 자유 서술 조건을 코드가 쓰는 태그로 옮긴다. 여기에 없는 서술은 그대로 둔다(Gleo 조건 등).
const TAGS = {
  '탄생 주행거리 1000km 초과': 'BIRTH_USED', '2년 차 이후': 'YEAR2+', '생일 당일에 운전하지 않음': 'LATE',
  '8일째 이후': 'DAY8+', '30일 이상': 'ABSENT30', '심각도 SERIOUS': 'SERIOUS', '사고 모드 (CRASH)': 'CRASH',
  '사고 모드': 'CRASH', '재방문': 'REVISIT', '첫 만남·재방문': '', '10000km': 'KM:10000',
  '쓰다듬기 하루 상한': 'PET_CAP', '지역 간식 첫 맛': 'FIRST_REGION_SNACK', '간식 하루 상한': 'SNACK_CAP',
  '주머니 5개': 'SNACK_FULL', '주머니 비었음': 'NO_SNACK', '새 물건 첫 배치': 'FIRST_DECO', '성장으로 칸 증가': 'SLOTS_UP',
  '30초 입력 없음': 'IDLE30', 'P 10분 이상': 'PARK10', '앰비언트 라이트 켜짐': 'AMBIENT', '충전 중': 'CHARGING',
  '이번 주 앨범 생성': 'WEEKLY_ALBUM', '앨범 열기': 'ALBUM_OPEN', '애정 80 이상': 'AFF80', '이번 주 앨범': 'WEEKLY_ALBUM',
  '도감 열기': 'DEX_OPEN', '쓰다듬기 연타': 'PET_ANNOY', '하루 기본 간식': 'SNACK_DAILY', '"아까 장면" 있음': 'RECAP', '손님 모드': 'GUEST', '팝업': '',
};

function stage(s) {
  s = s.trim();
  return ['E', 'B', 'B+', 'C+', 'A+', 'Y'].includes(s) ? s : '*'; // 시스템, 전 단계, (성장 순간), — 은 모든 단계
}

function cond(raw) {
  raw = raw.trim().replace(/(\d),(\d)/g, '$1$2');
  if (raw === '' || raw === '—') return '';
  return raw.split(',').map((c) => {
    c = c.trim();
    if (c in TAGS) return TAGS[c];
    const vis = c.match(/^(VIS-\d+) 첫 만남$/);
    if (vis) return 'VISITOR:' + vis[1];
    return c.replace(/\s+또는\s+/g, '|');
  }).filter((c) => c !== '').join(',');
}

const lines = fs.readFileSync(input, 'utf8').split(/\r?\n/);
const rows = [];
for (const line of lines) {
  if (!line.startsWith('| DLG-')) continue;
  // {펫이름|이/가} 안의 세로선은 열 구분이 아니다
  const safe = line.replace(/\{([^{}|]+)\|([^{}]+)\}/g, '{$1\u0001$2}');
  const cells = safe.slice(1, safe.lastIndexOf('|')).split('|').map((c) => c.trim().replace(/\u0001/g, '|'));
  const id = cells[0];
  const text = cells[1].replace(/	/g, " ") // EGG 줄의 굵은 의성어 표식 ** 는 그대로 둔다(앱이 굵게 그린다);
  const hasStage = cells.length >= 5;
  rows.push([id, hasStage ? stage(cells[2]) : '*', hasStage ? cond(cells[3]) : '', text].join('\t'));
}
fs.mkdirSync(path.dirname(output), { recursive: true });
fs.writeFileSync(output, rows.join('\n') + '\n', 'utf8');
console.log(rows.length + ' lines -> ' + output);
