import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:share_plus/share_plus.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';

import 'screens/settings_screen.dart';

const String kHomeUrl = 'https://carizon.shop/';
const String kInternalDomain = 'carizon.shop';
const String kInquiryUrl = 'https://carizon.shop/';

void main() {
  runApp(const CarizonApp());
}

class CarizonApp extends StatelessWidget {
  const CarizonApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Carizon',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF0D1B2A)),
        useMaterial3: true,
      ),
      home: const CarizonHomePage(),
    );
  }
}

class CarizonHomePage extends StatefulWidget {
  const CarizonHomePage({super.key});

  @override
  State<CarizonHomePage> createState() => _CarizonHomePageState();
}

class _CarizonHomePageState extends State<CarizonHomePage> {
  final Connectivity _connectivity = Connectivity();
  final WebViewCookieManager _cookieManager = WebViewCookieManager();

  StreamSubscription<List<ConnectivityResult>>? _connectivitySubscription;
  late final WebViewController _controller;

  bool _isOffline = false;
  double _progress = 0;
  String _currentUrl = kHomeUrl;
  String _versionLabel = '로딩 중...';
  String _packageName = '';

  @override
  void initState() {
    super.initState();
    _initWebView();
    _initConnectivity();
    _loadPackageInfo();
  }

  @override
  void dispose() {
    _connectivitySubscription?.cancel();
    super.dispose();
  }

  void _initWebView() {
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setNavigationDelegate(
        NavigationDelegate(
          onNavigationRequest: _handleNavigationRequest,
          onPageStarted: (url) {
            if (!mounted) {
              return;
            }
            setState(() {
              _currentUrl = url;
              _progress = 0.05;
            });
          },
          onPageFinished: (url) {
            if (!mounted) {
              return;
            }
            setState(() {
              _currentUrl = url;
              _progress = 1.0;
            });
          },
          onProgress: (progress) {
            if (!mounted) {
              return;
            }
            setState(() {
              _progress = (progress / 100).clamp(0.0, 1.0);
            });
          },
          onUrlChange: (change) {
            final url = change.url;
            if (!mounted || url == null || url.isEmpty) {
              return;
            }
            setState(() {
              _currentUrl = url;
            });
          },
          onWebResourceError: (error) {
            if (!mounted) {
              return;
            }
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text('페이지 로딩 오류: ${error.description}'),
                duration: const Duration(seconds: 2),
              ),
            );
          },
        ),
      )
      ..loadRequest(Uri.parse(kHomeUrl));
  }

  Future<NavigationDecision> _handleNavigationRequest(
    NavigationRequest request,
  ) async {
    final uri = Uri.tryParse(request.url);
    if (uri == null) {
      return NavigationDecision.prevent;
    }

    if (_isInternalUri(uri)) {
      return NavigationDecision.navigate;
    }

    final opened = await launchUrl(uri, mode: LaunchMode.externalApplication);
    if (!opened && mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('외부 링크를 열 수 없습니다.')));
    }
    return NavigationDecision.prevent;
  }

  bool _isInternalUri(Uri uri) {
    if (uri.scheme == 'about') {
      return true;
    }

    if (uri.scheme != 'http' && uri.scheme != 'https') {
      return false;
    }

    final host = uri.host.toLowerCase();
    return host == kInternalDomain || host.endsWith('.$kInternalDomain');
  }

  Future<void> _initConnectivity() async {
    final result = await _connectivity.checkConnectivity();
    _updateConnectivity(result);
    _connectivitySubscription = _connectivity.onConnectivityChanged.listen(
      _updateConnectivity,
    );
  }

  void _updateConnectivity(List<ConnectivityResult> results) {
    final offline = results.contains(ConnectivityResult.none);
    if (!mounted) {
      return;
    }
    setState(() {
      _isOffline = offline;
    });
  }

  Future<void> _loadPackageInfo() async {
    final info = await PackageInfo.fromPlatform();
    if (!mounted) {
      return;
    }
    setState(() {
      _versionLabel = '${info.version}+${info.buildNumber}';
      _packageName = info.packageName;
    });
  }

  Future<void> _shareCurrentPage() async {
    final currentUri = Uri.tryParse(_currentUrl) ?? Uri.parse(kHomeUrl);
    await SharePlus.instance.share(
      ShareParams(text: currentUri.toString(), subject: 'Carizon 페이지 공유'),
    );
  }

  Future<void> _openSettings() async {
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (context) => SettingsScreen(
          appVersion: _versionLabel,
          packageName: _packageName,
          onClearCache: _clearWebCache,
          onClearCookies: _clearCookies,
          onOpenInquiry: _openInquiryLink,
        ),
      ),
    );
  }

  Future<String> _clearWebCache() async {
    await _controller.clearCache();
    await _controller.clearLocalStorage();
    return '웹 캐시를 삭제했습니다.';
  }

  Future<String> _clearCookies() async {
    final deleted = await _cookieManager.clearCookies();
    return deleted ? '쿠키를 삭제했습니다.' : '삭제할 쿠키가 없습니다.';
  }

  Future<String> _openInquiryLink() async {
    final opened = await launchUrl(
      Uri.parse(kInquiryUrl),
      mode: LaunchMode.externalApplication,
    );
    return opened ? '문의 링크를 열었습니다.' : '문의 링크를 열 수 없습니다.';
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, result) async {
        if (didPop) {
          return;
        }
        if (await _controller.canGoBack()) {
          await _controller.goBack();
          return;
        }
        await SystemNavigator.pop();
      },
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Carizon'),
          actions: [
            IconButton(
              tooltip: '현재 페이지 공유',
              onPressed: _shareCurrentPage,
              icon: const Icon(Icons.share_outlined),
            ),
            IconButton(
              tooltip: '설정',
              onPressed: _openSettings,
              icon: const Icon(Icons.settings_outlined),
            ),
          ],
        ),
        body: SafeArea(
          child: Column(
            children: [
              if (_isOffline)
                _OfflineBanner(
                  onRefresh: () {
                    _controller.reload();
                  },
                ),
              if (_progress < 1.0)
                LinearProgressIndicator(
                  value: _progress == 0 ? null : _progress,
                  minHeight: 3,
                ),
              Expanded(child: WebViewWidget(controller: _controller)),
            ],
          ),
        ),
      ),
    );
  }
}

class _OfflineBanner extends StatelessWidget {
  const _OfflineBanner({required this.onRefresh});

  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.amber.shade200,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        child: Row(
          children: [
            const Icon(Icons.wifi_off_outlined, size: 18),
            const SizedBox(width: 8),
            const Expanded(child: Text('네트워크 연결이 끊겼습니다.')),
            TextButton(onPressed: onRefresh, child: const Text('새로고침')),
          ],
        ),
      ),
    );
  }
}
