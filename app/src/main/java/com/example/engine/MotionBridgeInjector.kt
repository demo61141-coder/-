package com.example.engine

object MotionBridgeInjector {

    fun inject(rawHtml: String): String {
        var modified = rawHtml

        modified = modified.replace(
            Regex("""\b(?:const|let|var)\s+SCENES\s*="""),
            "var SCENES = window.__MC_SCENES ="
        )
        modified = modified.replace(
            Regex("""\b(?:const|let|var)\s+TOTAL_DURATION\s*="""),
            "var TOTAL_DURATION = window.__MC_TOTAL_DURATION ="
        )

        val bridgeScript = "<script>\n$BRIDGE_JS\n</script>"

        return when {
            modified.contains("<head>", ignoreCase = true) -> {
                modified.replaceFirst(
                    Regex("<head>", RegexOption.IGNORE_CASE),
                    "<head>\n$bridgeScript"
                )
            }
            modified.contains("<body>", ignoreCase = true) -> {
                modified.replaceFirst(
                    Regex("<body>", RegexOption.IGNORE_CASE),
                    "<body>\n$bridgeScript"
                )
            }
            else -> "$bridgeScript\n$modified"
        }
    }

    private val BRIDGE_JS = """
(() => {
  if (window.__mcBridgeInstalled) return;
  window.__mcBridgeInstalled = true;

  const BASE_MS = 1000.0;
  window.__mcVirtualMs = BASE_MS;
  window.__mcCurrentSec = 0.0;
  window.__mcRootRenderCb = null;
  window.__mcPendingQueue = [];
  window.__mcElementOverrides = {};
  window.__mcFrameElements = [];
  window.__mcExportCanvas = null;
  window.__mcExportMode = false;
  window.__mcInitDone = false;

  performance.now = function() {
    return window.__mcVirtualMs;
  };
  Date.now = function() {
    return window.__mcVirtualMs;
  };

  window.requestAnimationFrame = function(cb) {
    if (typeof cb === 'function') {
      window.__mcRootRenderCb = cb;
      window.__mcPendingQueue.push(cb);
      if (!window.__mcInitDone) {
        window.__mcInitDone = true;
        setTimeout(() => {
          window.__mcSeekTo(window.__mcCurrentSec || 0.0);
        }, 30);
      }
    }
    return window.__mcPendingQueue.length;
  };

  const proto = CanvasRenderingContext2D.prototype;
  const origSave = proto.save;
  const origRestore = proto.restore;
  const origTranslate = proto.translate;
  const origScale = proto.scale;
  const origFillText = proto.fillText;
  const origStrokeText = proto.strokeText;
  const origFillRect = proto.fillRect;
  const origStrokeRect = proto.strokeRect;
  const origFill = proto.fill;
  const origStroke = proto.stroke;
  const origDrawImage = proto.drawImage;
  const origBeginPath = proto.beginPath;
  const origMoveTo = proto.moveTo;
  const origLineTo = proto.lineTo;
  const origRect = proto.rect;
  const origRoundRect = proto.roundRect;
  const origArc = proto.arc;
  const origArcTo = proto.arcTo;
  const origQuadraticCurveTo = proto.quadraticCurveTo;
  const origBezierCurveTo = proto.bezierCurveTo;
  const origEllipse = proto.ellipse;

  let saveStack = [];
  let gfxSeq = 0;
  let shapeSeq = 0;
  let imgSeq = 0;
  let activeSceneTag = 's0';
  let pathBounds = null;
  let activeShapeKey = null;
  let activeShapeOv = null;
  let activePathDx = 0;
  let activePathDy = 0;
  let groupRegistry = {};

  function resetPathState() {
    pathBounds = null;
    activeShapeKey = null;
    activeShapeOv = null;
    activePathDx = 0;
    activePathDy = 0;
  }

  function expandPathPoint(x, y) {
    if (window.__mcExportMode) return;
    if (!isFinite(x) || !isFinite(y)) return;
    if (!pathBounds) {
      pathBounds = { minX: x, minY: y, maxX: x, maxY: y };
    } else {
      if (x < pathBounds.minX) pathBounds.minX = x;
      if (y < pathBounds.minY) pathBounds.minY = y;
      if (x > pathBounds.maxX) pathBounds.maxX = x;
      if (y > pathBounds.maxY) pathBounds.maxY = y;
    }
  }

  function isSkippedByGroup() {
    for (let i = saveStack.length - 1; i >= 0; i--) {
      if (saveStack[i].deleted) return true;
    }
    return false;
  }

  function currentComponentGroupKey() {
    // Prefer depth >= 2 component groups (e.g. drawPerson, drawDocument, Clock, cards inside stage)
    // Or depth == 1 if it is NOT a large scene backdrop container
    for (let i = saveStack.length - 1; i >= 0; i--) {
      const fr = saveStack[i];
      if (fr.groupKey) {
        if (fr.depth >= 2) return fr.groupKey;
        const reg = groupRegistry[fr.groupKey];
        if (reg && (reg.maxY - reg.minY) > (reg.ch * 0.28) && (reg.maxX - reg.minX) > (reg.cw * 0.65)) {
          // Large stage container: let child shapes inside it have their own individual shapeKey!
          return null;
        }
        return fr.groupKey;
      }
    }
    return null;
  }

  function recordGraphicBox(ctx, key, kind, label, lx, ty, rx, by) {
    if (window.__mcExportMode) return;
    const canvas = ctx.canvas;
    const cw = (canvas && canvas.width) ? canvas.width : 1080;
    const ch = (canvas && canvas.height) ? canvas.height : 1920;
    if (cw <= 0 || ch <= 0) return;

    try {
      const m = ctx.getTransform();
      const p1 = m.transformPoint(new DOMPoint(lx, ty));
      const p2 = m.transformPoint(new DOMPoint(rx, by));
      const p3 = m.transformPoint(new DOMPoint(lx, by));
      const p4 = m.transformPoint(new DOMPoint(rx, ty));
      const minX = Math.max(0, Math.min(p1.x, p2.x, p3.x, p4.x));
      const maxX = Math.min(cw, Math.max(p1.x, p2.x, p3.x, p4.x));
      const minY = Math.max(0, Math.min(p1.y, p2.y, p3.y, p4.y));
      const maxY = Math.min(ch, Math.max(p1.y, p2.y, p3.y, p4.y));
      const bw = maxX - minX;
      const bh = maxY - minY;

      if (bw < 22 || bh < 18) return;
      if (bw > cw * 0.88 && bh > ch * 0.88) return;

      const existing = groupRegistry[key];
      if (!existing) {
        groupRegistry[key] = {
          key: key,
          kind: kind,
          originalText: label,
          displayText: label,
          minX: minX,
          minY: minY,
          maxX: maxX,
          maxY: maxY,
          cw: cw,
          ch: ch
        };
      } else {
        existing.minX = Math.min(existing.minX, minX);
        existing.minY = Math.min(existing.minY, minY);
        existing.maxX = Math.max(existing.maxX, maxX);
        existing.maxY = Math.max(existing.maxY, maxY);
      }
    } catch (e) {}
  }

  function applyGroupOverrideTransform(ctx, ov, cw, ch) {
    if (!ov) return;
    if (ov.offsetXNorm || ov.offsetYNorm) {
      const dx = (ov.offsetXNorm || 0) * cw;
      const dy = (ov.offsetYNorm || 0) * ch;
      const m = ctx.getTransform();
      const sx = Math.hypot(m.a, m.b) || 1;
      const sy = Math.hypot(m.c, m.d) || 1;
      origTranslate.call(ctx, dx / sx, dy / sy);
    }
    if (ov.scale && Math.abs(ov.scale - 1.0) > 0.01) {
      origScale.call(ctx, ov.scale, ov.scale);
    }
    if (ov.colorHex) {
      ctx.fillStyle = ov.colorHex;
      ctx.strokeStyle = ov.colorHex;
    }
  }

  proto.save = function() {
    origSave.call(this);
    saveStack.push({
      depth: saveStack.length + 1,
      groupKey: null,
      deleted: false,
      transformed: false
    });
  };

  proto.restore = function() {
    if (saveStack.length > 0) saveStack.pop();
    origRestore.call(this);
  };

  proto.translate = function(tx, ty) {
    origTranslate.call(this, tx, ty);
    if (saveStack.length > 0 && saveStack.length <= 2) {
      const frame = saveStack[saveStack.length - 1];
      if (!frame.transformed) {
        frame.transformed = true;
        gfxSeq++;
        const key = 'gfx_' + activeSceneTag + '_' + gfxSeq;
        frame.groupKey = key;
        const ov = window.__mcElementOverrides[key];
        if (ov) {
          if (ov.deleted) {
            frame.deleted = true;
          } else {
            const cw = (this.canvas && this.canvas.width) ? this.canvas.width : 1080;
            const ch = (this.canvas && this.canvas.height) ? this.canvas.height : 1920;
            applyGroupOverrideTransform(this, ov, cw, ch);
          }
        }
      }
    }
  };

  proto.beginPath = function() {
    resetPathState();
    origBeginPath.call(this);
    const compKey = currentComponentGroupKey();
    if (!compKey) {
      shapeSeq++;
      activeShapeKey = 'shape_' + activeSceneTag + '_' + shapeSeq;
      const ov = window.__mcElementOverrides[activeShapeKey];
      if (ov) {
        activeShapeOv = ov;
        if (!ov.deleted && (ov.offsetXNorm || ov.offsetYNorm)) {
          const cw = (this.canvas && this.canvas.width) ? this.canvas.width : 1080;
          const ch = (this.canvas && this.canvas.height) ? this.canvas.height : 1920;
          const m = this.getTransform();
          const sx = Math.hypot(m.a, m.b) || 1;
          const sy = Math.hypot(m.c, m.d) || 1;
          activePathDx = ((ov.offsetXNorm || 0) * cw) / sx;
          activePathDy = ((ov.offsetYNorm || 0) * ch) / sy;
        }
      }
    }
  };

  proto.moveTo = function(x, y) {
    const px = x + activePathDx, py = y + activePathDy;
    expandPathPoint(px, py);
    origMoveTo.call(this, px, py);
  };
  proto.lineTo = function(x, y) {
    const px = x + activePathDx, py = y + activePathDy;
    expandPathPoint(px, py);
    origLineTo.call(this, px, py);
  };
  proto.rect = function(x, y, w, h) {
    const px = x + activePathDx, py = y + activePathDy;
    expandPathPoint(px, py);
    expandPathPoint(px + w, py + h);
    origRect.call(this, px, py, w, h);
  };
  if (origRoundRect) {
    proto.roundRect = function(x, y, w, h, r) {
      const px = x + activePathDx, py = y + activePathDy;
      expandPathPoint(px, py);
      expandPathPoint(px + w, py + h);
      origRoundRect.call(this, px, py, w, h, r);
    };
  }
  proto.arc = function(x, y, r, s, e, ccw) {
    const px = x + activePathDx, py = y + activePathDy;
    expandPathPoint(px - r, py - r);
    expandPathPoint(px + r, py + r);
    origArc.call(this, px, py, r, s, e, ccw);
  };
  proto.arcTo = function(x1, y1, x2, y2, r) {
    const px1 = x1 + activePathDx, py1 = y1 + activePathDy;
    const px2 = x2 + activePathDx, py2 = y2 + activePathDy;
    expandPathPoint(px1, py1);
    expandPathPoint(px2, py2);
    origArcTo.call(this, px1, py1, px2, py2, r);
  };
  proto.quadraticCurveTo = function(cpx, cpy, x, y) {
    const pcx = cpx + activePathDx, pcy = cpy + activePathDy;
    const px = x + activePathDx, py = y + activePathDy;
    expandPathPoint(pcx, pcy);
    expandPathPoint(px, py);
    origQuadraticCurveTo.call(this, pcx, pcy, px, py);
  };
  proto.bezierCurveTo = function(cp1x, cp1y, cp2x, cp2y, x, y) {
    const p1x = cp1x + activePathDx, p1y = cp1y + activePathDy;
    const p2x = cp2x + activePathDx, p2y = cp2y + activePathDy;
    const px = x + activePathDx, py = y + activePathDy;
    expandPathPoint(p1x, p1y);
    expandPathPoint(p2x, p2y);
    expandPathPoint(px, py);
    origBezierCurveTo.call(this, p1x, p1y, p2x, p2y, px, py);
  };
  if (origEllipse) {
    proto.ellipse = function(x, y, rx, ry, rot, s, e, ccw) {
      const px = x + activePathDx, py = y + activePathDy;
      expandPathPoint(px - rx, py - ry);
      expandPathPoint(px + rx, py + ry);
      origEllipse.call(this, px, py, rx, ry, rot, s, e, ccw);
    };
  }

  proto.fillRect = function(x, y, w, h) {
    if (isSkippedByGroup()) return;
    const cw = (this.canvas && this.canvas.width) ? this.canvas.width : 1080;
    const ch = (this.canvas && this.canvas.height) ? this.canvas.height : 1920;
    const isBg = (Math.abs(w) > cw * 0.85 && Math.abs(h) > ch * 0.85);
    const compKey = currentComponentGroupKey();

    if (!isBg) {
      const key = compKey || ('rect_' + activeSceneTag + '_' + (++shapeSeq));
      const ov = compKey ? null : window.__mcElementOverrides[key];
      if (ov && ov.deleted) return;
      recordGraphicBox(this, key, 'GRAPHIC', 'Box #' + shapeSeq, x, y, x + w, y + h);
      if (ov) {
        origSave.call(this);
        applyGroupOverrideTransform(this, ov, cw, ch);
        origFillRect.call(this, x, y, w, h);
        origRestore.call(this);
        return;
      }
    }
    origFillRect.call(this, x, y, w, h);
  };

  proto.strokeRect = function(x, y, w, h) {
    if (isSkippedByGroup()) return;
    const cw = (this.canvas && this.canvas.width) ? this.canvas.width : 1080;
    const ch = (this.canvas && this.canvas.height) ? this.canvas.height : 1920;
    const compKey = currentComponentGroupKey();
    const key = compKey || ('srect_' + activeSceneTag + '_' + (++shapeSeq));
    const ov = compKey ? null : window.__mcElementOverrides[key];
    if (ov && ov.deleted) return;
    recordGraphicBox(this, key, 'GRAPHIC', 'Frame #' + shapeSeq, x, y, x + w, y + h);
    if (ov) {
      origSave.call(this);
      applyGroupOverrideTransform(this, ov, cw, ch);
      origStrokeRect.call(this, x, y, w, h);
      origRestore.call(this);
      return;
    }
    origStrokeRect.call(this, x, y, w, h);
  };

  proto.fill = function() {
    if (isSkippedByGroup()) return;
    if (activeShapeOv && activeShapeOv.deleted) return;
    const compKey = currentComponentGroupKey();
    if (compKey && pathBounds) {
      recordGraphicBox(
        this, compKey, 'GRAPHIC', 'Graphic (' + compKey + ')',
        pathBounds.minX, pathBounds.minY, pathBounds.maxX, pathBounds.maxY
      );
    } else if (activeShapeKey && pathBounds) {
      recordGraphicBox(
        this, activeShapeKey, 'GRAPHIC', 'Card / Shape #' + shapeSeq,
        pathBounds.minX, pathBounds.minY, pathBounds.maxX, pathBounds.maxY
      );
    }
    if (activeShapeOv && activeShapeOv.colorHex) {
      origSave.call(this);
      this.fillStyle = activeShapeOv.colorHex;
      origFill.apply(this, arguments);
      origRestore.call(this);
      return;
    }
    origFill.apply(this, arguments);
  };

  proto.stroke = function() {
    if (isSkippedByGroup()) return;
    if (activeShapeOv && activeShapeOv.deleted) return;
    const compKey = currentComponentGroupKey();
    if (compKey && pathBounds) {
      recordGraphicBox(
        this, compKey, 'GRAPHIC', 'Graphic (' + compKey + ')',
        pathBounds.minX, pathBounds.minY, pathBounds.maxX, pathBounds.maxY
      );
    } else if (activeShapeKey && pathBounds) {
      recordGraphicBox(
        this, activeShapeKey, 'GRAPHIC', 'Card / Shape #' + shapeSeq,
        pathBounds.minX, pathBounds.minY, pathBounds.maxX, pathBounds.maxY
      );
    }
    if (activeShapeOv && activeShapeOv.colorHex) {
      origSave.call(this);
      this.strokeStyle = activeShapeOv.colorHex;
      origStroke.apply(this, arguments);
      origRestore.call(this);
      return;
    }
    origStroke.apply(this, arguments);
  };

  proto.drawImage = function(img) {
    if (isSkippedByGroup()) return;
    if (this.canvas === window.__mcExportCanvas) {
      origDrawImage.apply(this, arguments);
      return;
    }
    const compKey = currentComponentGroupKey();
    const key = compKey || ('img_' + activeSceneTag + '_' + (++imgSeq));
    const ov = compKey ? null : window.__mcElementOverrides[key];
    if (ov && ov.deleted) return;

    let dx = 0, dy = 0, dw = 100, dh = 100;
    if (arguments.length === 3) {
      dx = arguments[1]; dy = arguments[2];
      dw = img.width || 100; dh = img.height || 100;
    } else if (arguments.length === 5) {
      dx = arguments[1]; dy = arguments[2]; dw = arguments[3]; dh = arguments[4];
    } else if (arguments.length >= 9) {
      dx = arguments[5]; dy = arguments[6]; dw = arguments[7]; dh = arguments[8];
    }
    recordGraphicBox(this, key, 'IMAGE', 'Picture #' + imgSeq, dx, dy, dx + dw, dy + dh);

    if (ov) {
      const cw = (this.canvas && this.canvas.width) ? this.canvas.width : 1080;
      const ch = (this.canvas && this.canvas.height) ? this.canvas.height : 1920;
      origSave.call(this);
      applyGroupOverrideTransform(this, ov, cw, ch);
      origDrawImage.apply(this, arguments);
      origRestore.call(this);
      return;
    }
    origDrawImage.apply(this, arguments);
  };

  function recordAndTransformText(ctx, text, x, y, drawFn, maxWidth) {
    if (isSkippedByGroup()) return;
    const clean = String(text || '').trim();
    if (!clean) {
      drawFn.call(ctx, text, x, y, maxWidth);
      return;
    }

    const key = clean;
    const ov = window.__mcElementOverrides[key];
    if (ov && ov.deleted) return;

    const finalText = (ov && ov.customText != null && ov.customText !== '') ? ov.customText : text;
    const canvas = ctx.canvas;
    const cw = (canvas && canvas.width) ? canvas.width : 1080;
    const ch = (canvas && canvas.height) ? canvas.height : 1920;

    if (!window.__mcExportMode) {
      try {
        const m = ctx.getTransform();
        const pt = m.transformPoint(new DOMPoint(x, y));
        const metrics = ctx.measureText(finalText);
        const scaleX = Math.hypot(m.a, m.b) || 1;
        const scaleY = Math.hypot(m.c, m.d) || 1;
        const textW = Math.max(40, metrics.width * scaleX);
        const fontMatch = /(\d+(?:\.\d+)?)px/.exec(ctx.font || '');
        const fontPx = fontMatch ? parseFloat(fontMatch[1]) : 28;
        const textH = Math.max(24, fontPx * 1.25 * scaleY);

        let leftX = pt.x;
        if (ctx.textAlign === 'center') leftX = pt.x - textW / 2;
        else if (ctx.textAlign === 'right' || ctx.textAlign === 'end') leftX = pt.x - textW;
        const topY = pt.y - textH * 0.85;

        const existing = window.__mcFrameElements.find(e => e.key === key);
        if (!existing && cw > 0 && ch > 0) {
          window.__mcFrameElements.push({
            key: key,
            kind: 'TEXT',
            originalText: clean,
            displayText: String(finalText).trim(),
            xNorm: Math.max(0, Math.min(1, leftX / cw)),
            yNorm: Math.max(0, Math.min(1, topY / ch)),
            wNorm: Math.max(0.05, Math.min(1, textW / cw)),
            hNorm: Math.max(0.025, Math.min(0.4, textH / ch))
          });
        }
      } catch (e) {}
    }

    origSave.call(ctx);
    if (ov) {
      if (ov.offsetXNorm || ov.offsetYNorm) {
        const dx = (ov.offsetXNorm || 0) * cw;
        const dy = (ov.offsetYNorm || 0) * ch;
        const m = ctx.getTransform();
        const sx = Math.hypot(m.a, m.b) || 1;
        const sy = Math.hypot(m.c, m.d) || 1;
        origTranslate.call(ctx, dx / sx, dy / sy);
      }
      if (ov.scale && Math.abs(ov.scale - 1.0) > 0.01) {
        origTranslate.call(ctx, x, y);
        origScale.call(ctx, ov.scale, ov.scale);
        origTranslate.call(ctx, -x, -y);
      }
      if (ov.colorHex) {
        ctx.fillStyle = ov.colorHex;
        ctx.strokeStyle = ov.colorHex;
      }
    }

    if (maxWidth !== undefined) {
      drawFn.call(ctx, finalText, x, y, maxWidth);
    } else {
      drawFn.call(ctx, finalText, x, y);
    }
    origRestore.call(ctx);
  }

  proto.fillText = function(text, x, y, maxWidth) {
    recordAndTransformText(this, text, x, y, origFillText, maxWidth);
  };

  proto.strokeText = function(text, x, y, maxWidth) {
    recordAndTransformText(this, text, x, y, origStrokeText, maxWidth);
  };

  window.__mcSeekTo = function(timeSec) {
    window.__mcCurrentSec = Math.max(0, timeSec);
    window.__mcVirtualMs = BASE_MS + window.__mcCurrentSec * 1000.0;
    window.__mcFrameElements = [];
    saveStack = [];
    gfxSeq = 0;
    shapeSeq = 0;
    imgSeq = 0;
    groupRegistry = {};
    resetPathState();

    let activeDeleted = false;
    activeSceneTag = 's0';
    if (Array.isArray(window.__MC_SCENES)) {
      for (let i = 0; i < window.__MC_SCENES.length; i++) {
        const s = window.__MC_SCENES[i];
        if (window.__mcCurrentSec >= s.start && window.__mcCurrentSec < s.end) {
          activeSceneTag = 's' + s.id;
          if (s.__mcDeleted) activeDeleted = true;
          break;
        }
      }
    }

    const q = window.__mcPendingQueue.splice(0, window.__mcPendingQueue.length);
    if (q.length > 0) {
      q.forEach(cb => {
        try { cb(window.__mcVirtualMs); } catch (e) {}
      });
    } else if (typeof window.__mcRootRenderCb === 'function') {
      try { window.__mcRootRenderCb(window.__mcVirtualMs); } catch (e) {}
    }

    if (activeDeleted) {
      const c = document.querySelector('canvas');
      if (c) {
        const cx = c.getContext('2d');
        if (cx) {
          origSave.call(cx);
          cx.setTransform(1, 0, 0, 1, 0, 0);
          cx.fillStyle = '#070b12';
          origFillRect.call(cx, 0, 0, c.width, c.height);
          cx.fillStyle = '#64748b';
          cx.font = 'bold 34px sans-serif';
          cx.textAlign = 'center';
          origFillText.call(cx, 'Scene Removed', c.width / 2, c.height / 2);
          origRestore.call(cx);
        }
      }
    }

    if (document.getAnimations) {
      try {
        document.getAnimations().forEach(anim => {
          anim.pause();
          anim.currentTime = window.__mcCurrentSec * 1000.0;
        });
      } catch (e) {}
    }

    if (window.__mcExportMode) return '';

    Object.keys(groupRegistry).forEach(k => {
      const g = groupRegistry[k];
      const w = g.maxX - g.minX;
      const h = g.maxY - g.minY;
      if (w >= 22 && h >= 18 && !(w > g.cw * 0.88 && h > g.ch * 0.88)) {
        const label = g.kind === 'IMAGE'
          ? ('Picture (' + Math.round(w) + '×' + Math.round(h) + ')')
          : ('Card / Graphic (' + Math.round(w) + '×' + Math.round(h) + ')');
        window.__mcFrameElements.push({
          key: g.key,
          kind: g.kind,
          originalText: label,
          displayText: label,
          xNorm: Math.max(0, Math.min(1, g.minX / g.cw)),
          yNorm: Math.max(0, Math.min(1, g.minY / g.ch)),
          wNorm: Math.max(0.04, Math.min(1, w / g.cw)),
          hNorm: Math.max(0.025, Math.min(1, h / g.ch))
        });
      }
    });

    return JSON.stringify({
      time: window.__mcCurrentSec,
      elements: window.__mcFrameElements
    });
  };

  window.__mcUpdateScenes = function(scenesJson, totalDuration) {
    try {
      const parsed = JSON.parse(scenesJson);
      if (Array.isArray(window.__MC_SCENES) && Array.isArray(parsed)) {
        for (let i = 0; i < parsed.length && i < window.__MC_SCENES.length; i++) {
          window.__MC_SCENES[i].start = parsed[i].start;
          window.__MC_SCENES[i].end = parsed[i].end;
          window.__MC_SCENES[i].headline = parsed[i].headline;
          window.__MC_SCENES[i].sub = parsed[i].sub;
          window.__MC_SCENES[i].caption = parsed[i].caption;
          window.__MC_SCENES[i].__mcDeleted = !!parsed[i].isDeleted;
        }
      }
      if (typeof totalDuration === 'number' && totalDuration > 0) {
        window.__MC_TOTAL_DURATION = totalDuration;
      }
      return window.__mcSeekTo(window.__mcCurrentSec);
    } catch (e) {
      return '{}';
    }
  };

  window.__mcSetElementOverrides = function(overridesJson) {
    try {
      const arr = JSON.parse(overridesJson);
      const map = {};
      if (Array.isArray(arr)) {
        arr.forEach(item => {
          if (item && item.key) map[item.key] = item;
        });
      }
      window.__mcElementOverrides = map;
      return window.__mcSeekTo(window.__mcCurrentSec);
    } catch (e) {
      return '{}';
    }
  };

  window.__mcSetExportMode = function(enabled) {
    window.__mcExportMode = !!enabled;
  };

  window.__mcCaptureFrameJpeg = function(targetW, targetH, quality) {
    const c = document.querySelector('canvas');
    if (!c) return '';
    if (!window.__mcExportCanvas) {
      window.__mcExportCanvas = document.createElement('canvas');
    }
    const ec = window.__mcExportCanvas;
    if (ec.width !== targetW || ec.height !== targetH) {
      ec.width = targetW;
      ec.height = targetH;
    }
    const ex = ec.getContext('2d', { alpha: false });
    ex.fillStyle = '#070b12';
    origFillRect.call(ex, 0, 0, targetW, targetH);
    origDrawImage.call(ex, c, 0, 0, targetW, targetH);
    return ec.toDataURL('image/jpeg', quality || 0.80);
  };

  window.__mcRenderAndCapture = function(timeSec, targetW, targetH, quality) {
    window.__mcExportMode = true;
    window.__mcSeekTo(timeSec);
    return window.__mcCaptureFrameJpeg(targetW, targetH, quality);
  };
})();
""".trimIndent()
}
