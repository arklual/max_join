import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Typography,
} from '@mui/material';

interface Section {
  title: string;
  items: string[];
}

// Keep in sync with the backend: AgePolicy, AccountDeletionService, IceBreakerService.
const SECTIONS: Section[] = [
  {
    title: 'Кто обрабатывает данные',
    items: [
      'Оператор — команда проекта JOIN. Сервис работает в пилотном режиме. Вопросы о данных — в поддержку: «Чаты» → «Поддержка».',
    ],
  },
  {
    title: 'Какие данные мы храним',
    items: [
      'Профиль: имя, пол, возраст, город, вуз, интересы, фото и текст «о себе», если вы их добавили.',
      'Вход: идентификатор MAX или Telegram; email и хеш пароля, если вы входите по email.',
      'Действия в JOIN: сохранённые события, совпадения и приглашения, сообщения в чатах, участие в компаниях, ответы «Сходили вместе?», чёрный список, настройки поиска и обращения в поддержку.',
    ],
  },
  {
    title: 'Зачем',
    items: [
      'Показывать афишу вашего города и находить компанию на события.',
      'Доставлять сообщения и уведомления через бота в мессенджере.',
      'Защищать пользователей: чёрный список и возрастные ограничения.',
      'Считать общую статистику пилота (сколько совпадений превратилось в походы) — без передачи третьим лицам.',
    ],
  },
  {
    title: 'Кто видит ваш профиль',
    items: [
      'Профиль — имя, пол, возраст, город, вуз, интересы, фото, статус и «о себе» — видят другие пользователи JOIN вашей возрастной группы, прежде всего напарники по совпадениям и участники компаний. Email и данные для входа не видны никому.',
      'Написать вам можно только после того, как вы согласились пойти вместе.',
    ],
  },
  {
    title: 'Возраст',
    items: [
      'JOIN доступен с 14 лет. Если вам от 14 до 18, пользуясь сервисом, вы подтверждаете, что родители или законные представители не возражают.',
      'Пользователи 14–17 лет находят компанию и видят профили только сверстников 14–17 лет, взрослые — только взрослых.',
      'Возраст указывает сам пользователь. Если видите, что кто-то указал неверный возраст, заблокируйте его и напишите в поддержку.',
    ],
  },
  {
    title: 'Кому передаются данные',
    items: [
      'Мессенджеру MAX (или Telegram) — чтобы бот доставил вам уведомления.',
      'Сервису генерации подсказок для первого сообщения — только категории интересов и данные о событии, без имени, возраста и других данных профиля.',
      'Данные не продаются и не передаются другим лицам. Билеты покупаются на сайтах продавцов — JOIN не передаёт им ваши данные.',
    ],
  },
  {
    title: 'Где и сколько хранятся',
    items: [
      'Данные хранятся, пока существует аккаунт. Пилотный сервер сейчас размещён за пределами России; перед публичным запуском данные будут перенесены на сервер в России.',
    ],
  },
  {
    title: 'Ваши права',
    items: [
      'Изменить данные — в профиле, кнопка «Редактировать».',
      'Отозвать согласие и удалить данные — «Профиль» → «Удалить аккаунт». Удаляются профиль, сохранённые события, совпадения, личные чаты с сообщениями и созданные вами компании; из компаний друзей вы выходите.',
      'Узнать, какие данные о вас хранятся, — через поддержку.',
    ],
  },
];

export const PRIVACY_POLICY_EDITION = 'Редакция от 29 сентября 2026 г.';

/** The policy text; shown as the /privacy page and in a dialog during sign-up. */
export function PrivacyPolicyText() {
  return (
    <>
      {SECTIONS.map((section) => (
        <Box key={section.title} component="section" sx={{ mb: 3, '&:last-child': { mb: 0 } }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>
            {section.title}
          </Typography>
          <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
            {section.items.map((item) => (
              <Typography key={item} component="li" variant="body2" sx={{ mb: 0.75, lineHeight: 1.6 }}>
                {item}
              </Typography>
            ))}
          </Box>
        </Box>
      ))}
    </>
  );
}

/** Opens the policy over the current screen, so a half-filled form isn't lost. */
export function PrivacyPolicyDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <Dialog open={open} onClose={onClose} scroll="paper" fullWidth maxWidth="sm">
      <DialogTitle sx={{ fontWeight: 700 }}>Политика конфиденциальности</DialogTitle>
      <DialogContent dividers>
        <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main', mb: 2 }}>
          {PRIVACY_POLICY_EDITION}
        </Typography>
        <PrivacyPolicyText />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} sx={{ textTransform: 'none' }}>Понятно</Button>
      </DialogActions>
    </Dialog>
  );
}
