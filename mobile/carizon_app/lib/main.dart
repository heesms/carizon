import 'dart:async';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';

const String kHomeUrl = 'https://carizon.shop/';
const String kHomeDomain = 'carizon.shop';
const String kCarizonLogoAsset = 'assets/images/app_icon2.png';
const Set<String> kInternalDomains = {
  kHomeDomain,
  'm.kbchachacha.com',
  'charancha.com',
  'www.chutcha.net',
  'fem.encar.com',
  'mycarsave.lotterentacar.net',
  'm.kcar.com',
};

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

  StreamSubscription<List<ConnectivityResult>>? _connectivitySubscription;
  late final WebViewController _controller;

  bool _isOffline = false;
  double _progress = 0;
  String _currentUrl = kHomeUrl;

  @override
  void initState() {
    super.initState();
    _initWebView();
    _initConnectivity();
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
            // 서브리소스 오류(ORB 차단 등)는 무시, 메인 프레임 오류만 표시
            if (error.isForMainFrame != true) {
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
    return kInternalDomains.any(
      (domain) => host == domain || host.endsWith('.$domain'),
    );
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

  bool _isPlatformPage() {
    final uri = Uri.tryParse(_currentUrl);
    if (uri == null) {
      return false;
    }

    if (!_isInternalUri(uri)) {
      return false;
    }

    return !_isHomeHost(uri);
  }

  String _currentPlatformLabel() {
    final uri = Uri.tryParse(_currentUrl);
    if (uri == null) {
      return '';
    }

    final host = uri.host.toLowerCase();
    if (host.isEmpty) {
      return '';
    }

    if (host.startsWith('www.')) {
      return host.substring(4);
    }
    return host;
  }

  bool _isHomeHost(Uri uri) {
    final host = uri.host.toLowerCase();
    return host == kHomeDomain || host.endsWith('.$kHomeDomain');
  }

  Future<void> _closePlatformPage() async {
    if (await _controller.canGoBack()) {
      await _controller.goBack();
      return;
    }
    await _controller.loadRequest(Uri.parse(kHomeUrl));
  }

  @override
  Widget build(BuildContext context) {
    final showCloseButton = _isPlatformPage();
    final showPlatformHeader = showCloseButton;

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
              if (showPlatformHeader)
                _PlatformBrowserHeader(
                  onClose: _closePlatformPage,
                  label: _currentPlatformLabel(),
                ),
              Expanded(
                child: Stack(
                  children: [
                    WebViewWidget(controller: _controller),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _CarizonLogoBadge extends StatelessWidget {
  const _CarizonLogoBadge({this.size = 32});

  final double size;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: size,
      width: size,
      child: Image.asset(
        kCarizonLogoAsset,
        width: size,
        height: size,
        fit: BoxFit.contain,
        errorBuilder: (context, error, stackTrace) {
          return const Icon(Icons.directions_car_filled, size: 18, color: Color(0xFF1F2937));
        },
      ),
    );
  }
}

class _FloatingToolbarButton extends StatelessWidget {
  const _FloatingToolbarButton({
    required this.icon,
    required this.tooltip,
    required this.onPressed,
  });

  final IconData icon;
  final String tooltip;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: Colors.black.withOpacity(0.82),
        boxShadow: const [
          BoxShadow(color: Color(0x33000000), blurRadius: 6, offset: Offset(0, 2)),
        ],
      ),
      child: IconButton(
        visualDensity: VisualDensity.compact,
        tooltip: tooltip,
        color: Colors.white,
        icon: Icon(icon),
        onPressed: onPressed,
      ),
    );
  }
}

class _PlatformBrowserHeader extends StatelessWidget {
  const _PlatformBrowserHeader({required this.onClose, required this.label});

  final VoidCallback onClose;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      height: 52,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFFE5E7EB)),
        boxShadow: const [
          BoxShadow(
            color: Color(0x14000000),
            blurRadius: 12,
            offset: Offset(0, 3),
          ),
        ],
      ),
      child: Material(
        color: Colors.transparent,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 10),
          child: Row(
            children: [
              const _CarizonLogoBadge(size: 28),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      '외부 플랫폼',
                      style: TextStyle(fontSize: 10, color: Color(0xFF64748B)),
                    ),
                    Text(
                      label,
                      style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w700,
                        color: Color(0xFF1F2937),
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
              _FloatingToolbarButton(
                icon: Icons.close,
                tooltip: '이전 페이지로',
                onPressed: onClose,
              ),
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
