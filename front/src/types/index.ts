export interface User {
  id: number;
  maxId: number;
  name: string;
  age: number;
  city: string;
  bio: string;
  avatarUrl: string | null;
  registered: boolean;
  email?: string | null;
}

export interface Event {
  id: number;
  title: string;
  description: string;
  date: string;
  city: string;
  category: string;
  imageUrl: string | null;
  organizerId: number;
}

export interface Chat {
  id: number;
  companionId: number;
  eventId: number;
  eventTitle: string;
  companionName: string | null;
  companionPhoto: string | null;
  lastMessage: string | null;
  lastMessageTime: string | null;
  unreadCount: number;
}

export interface ChatMessage {
  id: number;
  /** Present on WebSocket pushes; used to ignore frames for other chats. */
  chatId?: number;
  senderId: number;
  text: string;
  createdAt: string;
  isRead: boolean;
}

export interface ApiError {
  message: string;
  status: number;
}

export interface EventCard {
  id: number;
  title: string;
  type: string;
  imageUrl: string;
  price: number | null;
  originalPrice?: number | null;
  studentPromoCode?: string | null;
  studentPromoNote?: string | null;
  eventDate: string;
  eventTime: string;
  city: string;
  liked: boolean;
  hasMatch?: boolean;
  /** Payable with the Pushkin card (Пушкинская карта). */
  pushkinCard?: boolean;
}

export interface EventDetail extends EventCard {
  description: string;
  ticketUrl: string;
  hasMatch: boolean;
}

export interface Tag {
  id: number;
  name: string;
  usageCount?: number;
}

export interface EventFilters {
  search?: string;
  minPrice?: number;
  maxPrice?: number;
  dateFrom?: string;
  dateTo?: string;
  tagIds?: number[];
  /** Only events payable with the Pushkin card. */
  pushkinCard?: boolean;
}

export interface PageResponse<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
  last: boolean;
}

export type InterestType =
  | 'CAREER'
  | 'THEATER'
  | 'ART'
  | 'MUSIC'
  | 'SPORT'
  | 'CINEMA'
  | 'MASTER_CLASS'
  | 'EXCURSION'
  | 'FESTIVAL';

export const INTEREST_LABELS: Record<InterestType, string> = {
  CAREER: 'Карьерные',
  THEATER: 'Театр',
  ART: 'Искусство',
  MUSIC: 'Музыка',
  SPORT: 'Спорт',
  CINEMA: 'Кино',
  MASTER_CLASS: 'Мастер-класс',
  EXCURSION: 'Экскурсия',
  FESTIVAL: 'Фестиваль',
};

export const ALL_INTERESTS: InterestType[] = [
  'CAREER',
  'THEATER',
  'ART',
  'MUSIC',
  'SPORT',
  'CINEMA',
  'MASTER_CLASS',
  'EXCURSION',
  'FESTIVAL',
];

// ===== Support Chat =====

export type SupportSenderType = 'USER' | 'OPERATOR';
export type SupportTicketStatus = 'OPEN' | 'CLOSED';

export interface SupportTicket {
  id: number;
  userId: number;
  status: SupportTicketStatus;
  createdAt: string;
  lastMessageAt: string | null;
  unreadCount: number;
}

export interface SupportMessage {
  id: number;
  ticketId: number;
  senderType: SupportSenderType;
  text: string;
  createdAt: string;
  isRead: boolean;
}

export interface UserProfile {
  id: number;
  maxId: number;
  email: string;
  city: string;
  firstName: string;
  lastName?: string;
  gender: string;
  age: number;
  photo?: string;
  interests: InterestType[];
  telegramChannel?: string;
  status?: string;
  bio?: string;
  universityId?: number | null;
  universityName?: string;
  /** Email + password sign-in is set up. */
  hasPassword?: boolean;
}

export interface SearchCriteria {
  preferredAgeMin: number | null;
  preferredAgeMax: number | null;
  preferredGender: 'MALE' | 'FEMALE' | null;
  preferredUniversityId: number | null;
}

export interface CompanionProfile {
  id: number;
  firstName: string;
  lastName?: string;
  city: string;
  age: number;
  photo?: string;
  interests: InterestType[];
  universityName?: string;
  status?: string;
  bio?: string;
  gender: string;
}

// ===== Group Events =====

export type GroupStatus = 'OPEN' | 'FULL' | 'CLOSED';
export type GroupMemberRole = 'CREATOR' | 'MEMBER';

export interface GroupMemberPreview {
  id: number;
  firstName: string;
  photo: string | null;
}

export interface EventGroup {
  id: number;
  eventId: number;
  eventTitle?: string;
  eventDate?: string;
  title: string | null;
  description: string | null;
  maxSize: number;
  currentSize: number;
  status: GroupStatus;
  creator: GroupMemberPreview;
  memberPreviews?: GroupMemberPreview[];
  members?: EventGroupMember[];
  groupChatId?: number;
  createdAt: string;
}

export interface EventGroupMember {
  userId: number;
  firstName: string;
  photo: string | null;
  role: GroupMemberRole;
  joinedAt: string;
}

export interface EventGroupDetail extends EventGroup {
  members: EventGroupMember[];
}

export interface CreateGroupRequest {
  title?: string;
  description?: string;
  maxSize: number;
}

export interface GroupMemberResponse {
  userId: number;
  firstName: string;
  photo: string | null;
  role: GroupMemberRole;
  joinedAt: string;
}

export interface GroupResponse {
  id: number;
  eventId: number;
  eventTitle?: string;
  eventDate?: string;
  title: string | null;
  description: string | null;
  maxSize: number;
  currentSize: number;
  status: GroupStatus;
  groupChatId: number | null;
  creator: GroupMemberResponse;
  members: GroupMemberResponse[];
  createdAt: string;
}

export interface JoinGroupResponse {
  groupId: number;
  groupChatId: number;
  message: string;
}

export interface LeaveGroupResponse {
  groupId: number;
  message: string;
}

// ===== Friend Groups (постоянная группа друзей с intersection лайков) =====

export type FriendGroupMemberRoleType = 'CREATOR' | 'MEMBER';

export interface FriendGroupMember {
  userId: number;
  firstName: string | null;
  photo: string | null;
  role: FriendGroupMemberRoleType;
  joinedAt: string;
}

export interface FriendGroup {
  id: number;
  name: string;
  inviteCode: string;
  maxSize: number;
  currentSize: number;
  creator: FriendGroupMember | null;
  members: FriendGroupMember[];
  createdAt: string;
}

export interface CreateFriendGroupRequest {
  name: string;
  maxSize: number;
}

export interface JoinFriendGroupRequest {
  inviteCode: string;
}

export interface JoinFriendGroupResponse {
  friendGroupId: number;
  name: string;
  message: string;
}

export interface FriendGroupChatMessage {
  id: number;
  senderId: number;
  senderName: string | null;
  senderPhoto: string | null;
  text: string;
  createdAt: string;
}

// ===== Ice Breaker =====

export interface IceBreakerEventInfo {
  id: number;
  title: string;
  eventDate: string | null;
  eventTime: string | null;
  price: string | null;
  ticketUrl: string | null;
}

export interface IceBreakerResponse {
  suggestions: string[];
  event: IceBreakerEventInfo | null;
}

// ===== Notifications =====

export type NotificationType = 'MATCH' | 'CHAT_MESSAGE' | 'GROUP_INVITE' | 'GENERAL';

export interface AppNotification {
  id: number;
  type: NotificationType;
  title: string;
  message: string;
  matchId: number | null;
  companionId: number | null;
  companionFirstName: string | null;
  companionPhoto: string | null;
  eventId: number | null;
  eventTitle: string | null;
  chatId: number | null;
  read: boolean;
  createdAt: string;
}
