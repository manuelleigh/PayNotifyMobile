// App.jsx
import React, { useEffect, useState, useCallback } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {
  NativeModules,
  NativeEventEmitter,
  View,
  Text,
  StyleSheet,
  ActivityIndicator,
  Pressable,
  StatusBar,
  AppState,
  Switch,
} from 'react-native';
import LoginScreen from './src/screens/LoginScreen';

const { TokenModule, NotificationAccessModule } = NativeModules;

const STORAGE_TOKEN = 'token';
const STORAGE_ENABLED_PKGS = 'enabled_packages';

// Catálogo de bancos soportados (paquete -> etiqueta)
const BANKS = [
  { key: 'yape', label: 'Yape', pkg: 'com.bcp.innovacxion.yapeapp' },
  { key: 'bbva', label: 'BBVA', pkg: 'com.bbva.nxt_peru' },
  {
    key: 'interbank',
    label: 'Interbank (Plin)',
    pkg: 'pe.com.interbank.mobilebanking',
  },
  {
    key: 'scotia',
    label: 'Scotiabank',
    pkg: 'pe.com.scotiabank.blpm.android.client',
  },
];

export default function App() {
  const [token, setToken] = useState(null);
  const [booting, setBooting] = useState(true);

  const forceRebind = async () => {
    try {
      await NotificationAccessModule?.forceRebindListener?.();
    } catch (e) {}
  };

  // Permiso Notification Listener
  const [notifAccess, setNotifAccess] = useState(null);
  const [checkingAccess, setCheckingAccess] = useState(false);

  // Selección de bancos (paquetes habilitados)
  const [enabledPkgs, setEnabledPkgs] = useState(new Set());

  const refreshNotifAccess = useCallback(async () => {
    if (!NotificationAccessModule?.isNotificationAccessEnabled) {
      setNotifAccess(null);
      return;
    }
    try {
      setCheckingAccess(true);
      const enabled =
        await NotificationAccessModule.isNotificationAccessEnabled();
      setNotifAccess(Boolean(enabled));
    } catch (e) {
      setNotifAccess(null);
    } finally {
      setCheckingAccess(false);
    }
  }, []);

  const pushEnabledPkgsToNative = useCallback(pkgsSet => {
    try {
      const arr = Array.from(pkgsSet);
      TokenModule?.setEnabledPackages?.(arr);
    } catch (e) {
      // No rompe la app
    }
  }, []);

  const loadEnabledPkgs = useCallback(async () => {
    const raw = await AsyncStorage.getItem(STORAGE_ENABLED_PKGS);

    let pkgs = [];
    if (raw) {
      try {
        pkgs = JSON.parse(raw);
      } catch {
        pkgs = [];
      }
    }
    if (!Array.isArray(pkgs) || pkgs.length === 0) {
      pkgs = ['com.bcp.innovacxion.yapeapp'];
      await AsyncStorage.setItem(STORAGE_ENABLED_PKGS, JSON.stringify(pkgs));
    }

    const set = new Set(pkgs);
    setEnabledPkgs(set);
    pushEnabledPkgsToNative(set);
  }, [pushEnabledPkgsToNative]);

  const saveEnabledPkgs = useCallback(
    async pkgsSet => {
      const arr = Array.from(pkgsSet);
      await AsyncStorage.setItem(STORAGE_ENABLED_PKGS, JSON.stringify(arr));
      pushEnabledPkgsToNative(pkgsSet);
    },
    [pushEnabledPkgsToNative],
  );

  // Boot
  useEffect(() => {
    (async () => {
      try {
        const savedToken = await AsyncStorage.getItem(STORAGE_TOKEN);
        await loadEnabledPkgs();

        if (savedToken) {
          setToken(savedToken);
          TokenModule?.setToken?.(savedToken);
          try {
            const invalid = await TokenModule?.getAuthInvalid?.();
            if (invalid) {
              await AsyncStorage.removeItem(STORAGE_TOKEN);
              setToken(null);
            }
          } catch (e) {}
        }
      } finally {
        setBooting(false);
      }
    })();
  }, [loadEnabledPkgs]);

  useEffect(() => {
    if (!TokenModule) return;

    const emitter = new NativeEventEmitter(TokenModule);
    const sub = emitter.addListener('PayNotifyAuthInvalid', () => {
      forceLogoutAuthInvalid();
    });

    return () => sub.remove();
  }, [forceLogoutAuthInvalid]);

  // Revisa permiso
  useEffect(() => {
    refreshNotifAccess();

    const sub = AppState.addEventListener('change', state => {
      if (state === 'active') {
        refreshNotifAccess();
      }
    });

    return () => sub?.remove?.();
  }, [refreshNotifAccess]);

  const onLogin = async newToken => {
    setToken(newToken);
    await AsyncStorage.setItem(STORAGE_TOKEN, newToken);
    TokenModule?.setToken?.(newToken);
    refreshNotifAccess();
    pushEnabledPkgsToNative(enabledPkgs);
  };

  const onLogout = async () => {
    await AsyncStorage.removeItem(STORAGE_TOKEN);
    setToken(null);
    TokenModule?.setToken?.('');
  };

  const openNotifSettings = () => {
    NotificationAccessModule?.openNotificationAccessSettings?.();
  };

  const forceLogoutAuthInvalid = useCallback(async () => {
    await AsyncStorage.removeItem(STORAGE_TOKEN);
    setToken(null);
    TokenModule?.setToken?.('');
    TokenModule?.clearAuthInvalid?.();
  }, []);

  const toggleBank = async pkg => {
    const next = new Set(enabledPkgs);
    if (next.has(pkg)) next.delete(pkg);
    else next.add(pkg);

    if (next.size === 0) {
      next.add('com.bcp.innovacxion.yapeapp');
    }

    setEnabledPkgs(next);
    await saveEnabledPkgs(next);
  };

  if (booting) {
    return (
      <View style={styles.boot}>
        <StatusBar barStyle="dark-content" />
        <ActivityIndicator size="large" />
        <Text style={styles.bootText}>Iniciando PayNotify...</Text>
      </View>
    );
  }

  if (!token) {
    return <LoginScreen onLogin={onLogin} />;
  }

  const permissionLabel =
    notifAccess === null ? '—' : notifAccess ? 'ACTIVO' : 'INACTIVO';

  return (
    <View style={styles.container}>
      <StatusBar barStyle="dark-content" />

      <View style={styles.header}>
        <Text style={styles.brand}>PayNotify</Text>
        <Text style={styles.subtitle}>
          Monitoreo de notificaciones bancarias
        </Text>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Estado</Text>
        <View style={styles.row}>
          <View style={[styles.dot, { backgroundColor: '#16a34a' }]} />
          <Text style={styles.rowText}>Sesión activa</Text>
        </View>
        <View style={styles.row}>
          <View style={[styles.dot, { backgroundColor: '#7c3aed' }]} />
          <Text style={styles.rowText}>
            Listo para capturar y enviar eventos
          </Text>
        </View>
        <Text style={styles.hint}>
          Se enviarán notificaciones solo de los bancos que actives abajo.
        </Text>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Bancos monitoreados</Text>
        {BANKS.map(b => {
          const enabled = enabledPkgs.has(b.pkg);
          return (
            <View key={b.pkg} style={styles.bankRow}>
              <View style={{ flex: 1 }}>
                <Text style={styles.bankName}>{b.label}</Text>
                <Text style={styles.bankPkg}>{b.pkg}</Text>
              </View>
              <Switch
                value={enabled}
                onValueChange={() => toggleBank(b.pkg)}
                trackColor={{ false: '#e2e8f0', true: '#c4b5fd' }}
                thumbColor={enabled ? '#5B2EFF' : '#94a3b8'}
              />
            </View>
          );
        })}
        <Text style={styles.hint}>
          Tip: activa solo los que uses para reducir “ruido” y ahorrar batería.
        </Text>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Permiso de notificaciones</Text>
        <View style={styles.row}>
          <View
            style={[
              styles.dot,
              {
                backgroundColor:
                  notifAccess === null
                    ? '#94a3b8'
                    : notifAccess
                    ? '#16a34a'
                    : '#dc2626',
              },
            ]}
          />
          <Text style={styles.rowText}>
            Acceso a notificaciones: {permissionLabel}
          </Text>
        </View>
        <Text style={styles.hint}>
          Este permiso es obligatorio. Si está inactivo, activa el acceso en
          Ajustes.
        </Text>
        <View style={{ marginTop: 12 }}>
          {!notifAccess && (
            <Pressable
              style={styles.btnPrimary}
              onPress={openNotifSettings}
              disabled={
                !NotificationAccessModule?.openNotificationAccessSettings
              }
            >
              <Text style={styles.btnPrimaryText}>Activar en Ajustes</Text>
            </Pressable>
          )}
          <Pressable
            style={styles.btnGhost}
            onPress={refreshNotifAccess}
            disabled={checkingAccess}
          >
            <Text style={styles.btnGhostText}>
              {checkingAccess ? 'Revisando...' : 'Revisar estado'}
            </Text>
          </Pressable>
        </View>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Acciones</Text>
        <Pressable style={styles.btnSecondary} onPress={onLogout}>
          <Text style={styles.btnSecondaryText}>Cerrar sesión</Text>
        </Pressable>
        <Pressable style={styles.btnGhost} onPress={forceRebind}>
          <Text style={styles.btnGhostText}>Reinicializar captura</Text>
        </Pressable>
      </View>

      <Text style={styles.footer}>PayNotify Mobile</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  boot: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
    backgroundColor: '#f8fafc',
  },
  bootText: {
    marginTop: 12,
    fontSize: 14,
    color: '#334155',
  },
  container: {
    flex: 1,
    backgroundColor: '#f8fafc',
    paddingHorizontal: 18,
    paddingTop: 30,
  },
  header: { marginBottom: 14 },
  brand: { fontSize: 28, fontWeight: '800', color: '#0f172a' },
  subtitle: { marginTop: 4, fontSize: 14, color: '#475569' },
  card: {
    backgroundColor: '#ffffff',
    borderRadius: 16,
    padding: 16,
    marginTop: 12,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  cardTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: '#0f172a',
    marginBottom: 10,
  },
  row: { flexDirection: 'row', alignItems: 'center', marginBottom: 8 },
  dot: { width: 10, height: 10, borderRadius: 10, marginRight: 10 },
  rowText: { fontSize: 14, color: '#0f172a', flexShrink: 1 },
  hint: { marginTop: 10, fontSize: 12, color: '#64748b', lineHeight: 16 },
  bankRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    borderTopWidth: 1,
    borderTopColor: '#f1f5f9',
  },
  bankName: { fontSize: 14, fontWeight: '800', color: '#0f172a' },
  bankPkg: { marginTop: 2, fontSize: 12, color: '#64748b' },
  btnPrimary: {
    borderRadius: 12,
    paddingVertical: 12,
    paddingHorizontal: 14,
    backgroundColor: '#5B2EFF',
    alignItems: 'center',
  },
  btnPrimaryText: { color: '#ffffff', fontWeight: '800', fontSize: 14 },
  btnGhost: {
    marginTop: 10,
    borderRadius: 12,
    paddingVertical: 12,
    paddingHorizontal: 14,
    backgroundColor: '#f1f5f9',
    borderWidth: 1,
    borderColor: '#e2e8f0',
    alignItems: 'center',
  },
  btnGhostText: { color: '#0f172a', fontWeight: '800', fontSize: 14 },
  btnSecondary: {
    borderRadius: 12,
    paddingVertical: 12,
    paddingHorizontal: 14,
    backgroundColor: '#334155',
    alignItems: 'center',
  },
  btnSecondaryText: { color: '#ffffff', fontWeight: '800', fontSize: 14 },
  footer: {
    marginTop: 14,
    textAlign: 'center',
    fontSize: 12,
    color: '#94a3b8',
  },
});
