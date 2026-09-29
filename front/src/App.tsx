import { BrowserRouter, Routes, Route, Navigate } from 'react-router';
import M3 from './theme/M3/M3';
import SplashScreen from './pages/SplashScreen';
import RegistrationScreen from './pages/RegistrationScreen';
import LoginScreen from './pages/LoginScreen';
import PrivacyScreen from './pages/PrivacyScreen';
import NativeBridge from './components/NativeBridge';
import MaxBackButton from './components/MaxBackButton';
import { LikeFeedbackHost } from './components/LikeFeedback';
import ErrorBoundary from './components/ErrorBoundary';
import AfishaScreen from './pages/AfishaScreen';
import LikesScreen from './pages/LikesScreen';
import ChatsListScreen from './pages/ChatsListScreen';
import ChatScreen from './pages/ChatScreen';
import SupportChatScreen from './pages/SupportChatScreen';
import ProfileScreen from './pages/ProfileScreen';
import CompanionProfileScreen from './pages/CompanionProfileScreen';
import EventDetailScreen from './pages/EventDetailScreen';
import GroupsScreen from './pages/GroupsScreen';
import GroupInviteScreen from './pages/GroupInviteScreen';
import GroupChatScreen from './pages/GroupChatScreen';
import FriendGroupsScreen from './pages/FriendGroupsScreen';
import FriendGroupDetailScreen from './pages/FriendGroupDetailScreen';
import FriendGroupChatScreen from './pages/FriendGroupChatScreen';
import SearchPreferencesScreen from './pages/SearchPreferencesScreen';
import MainLayout from './components/MainLayout';
import './index.css';

export default function App() {
  return (
    <M3>
      <ErrorBoundary>
      <BrowserRouter>
        <NativeBridge />
        <MaxBackButton />
        <LikeFeedbackHost />
        <Routes>
          <Route path="/" element={<SplashScreen />} />
          <Route path="/register" element={<RegistrationScreen />} />
          <Route path="/login" element={<LoginScreen />} />
          <Route path="/privacy" element={<PrivacyScreen />} />
          <Route element={<MainLayout />}>
            <Route path="/afisha" element={<AfishaScreen />} />
            <Route path="/likes" element={<LikesScreen />} />
            <Route path="/chats" element={<ChatsListScreen />} />
            <Route path="/chats/:id" element={<ChatScreen />} />
            <Route path="/support" element={<SupportChatScreen />} />
            <Route path="/profile" element={<ProfileScreen />} />
            <Route path="/search-preferences" element={<SearchPreferencesScreen />} />
            <Route path="/events/:id" element={<EventDetailScreen />} />
            <Route path="/profile/:userId" element={<CompanionProfileScreen />} />
            <Route path="/groups" element={<GroupsScreen />} />
            <Route path="/groups/:id" element={<GroupInviteScreen />} />
            <Route path="/group-chats/:groupChatId" element={<GroupChatScreen />} />
            <Route path="/friend-groups" element={<FriendGroupsScreen />} />
            <Route path="/friend-groups/:id" element={<FriendGroupDetailScreen />} />
            <Route path="/friend-groups/:id/chat" element={<FriendGroupChatScreen />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
      </ErrorBoundary>
    </M3>
  );
}
