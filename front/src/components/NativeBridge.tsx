import { useEffect } from 'react';
import { useNavigate } from 'react-router';
import { hideSplash, registerBackButton } from '../api/native';

/** Wires Android-only behaviour (hardware back, native splash) into the router. */
export default function NativeBridge() {
  const navigate = useNavigate();

  useEffect(() => {
    void registerBackButton(() => navigate(-1));
    void hideSplash();
  }, [navigate]);

  return null;
}
