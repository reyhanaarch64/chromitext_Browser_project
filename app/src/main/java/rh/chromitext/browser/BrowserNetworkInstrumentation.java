package rh.chromitext.browser;

/** Builds the optional fetch/XHR instrumentation injected by the Network debugger. */
public final class BrowserNetworkInstrumentation {
    private BrowserNetworkInstrumentation() { }

    public static String buildJavaScript() {
        StringBuilder js = new StringBuilder();
        js.append("(function(){var w=window;");
        js.append("if(w.__chromitextNetInstalled){w.__chromitextNetActive=true;return;}");
        js.append("w.__chromitextNetInstalled=true;w.__chromitextNetActive=true;");
        js.append("function emit(o){if(!w.__chromitextNetActive)return;try{console.info('__CHROMITEXT_NET__'+JSON.stringify(o));}catch(e){}}");
        js.append("function clip(v,n){try{v=String(v==null?'':v);return v.length>n?v.slice(0,n)+' [truncated]':v;}catch(e){return '[unavailable]';}}");
        js.append("function headersOf(h){var o={};try{if(!h)return o;if(typeof Headers!=='undefined'&&h instanceof Headers){h.forEach(function(v,k){o[k]=v;});}else if(Array.isArray(h)){h.forEach(function(p){if(p&&p.length>1)o[p[0]]=p[1];});}else{Object.keys(h).forEach(function(k){o[k]=h[k];});}}catch(e){}return o;}");
        js.append("var oldFetch=w.fetch;if(typeof oldFetch==='function'){w.fetch=function(input,init){");
        js.append("var u=typeof input==='string'?input:((input&&input.url)||String(input));var m=(init&&init.method)||((input&&input.method)||'GET');");
        js.append("var rh=headersOf((init&&init.headers)||((input&&input.headers)||null));var rb=(init&&typeof init.body!=='undefined')?clip(init.body,4000):'';var start=Date.now();");
        js.append("return oldFetch.apply(this,arguments).then(function(r){var h={};try{r.headers.forEach(function(v,k){h[k]=v;});}catch(e){}");
        js.append("var emitResponse=function(body){emit({type:'fetch',method:m,url:r.url||u,status:r.status,statusText:r.statusText,requestHeaders:rh,requestBody:rb,headers:h,responseBody:clip(body,5000),ms:Date.now()-start});};");
        js.append("try{r.clone().text().then(function(body){emitResponse(body);},function(){emitResponse('[response body unavailable]');});}catch(e){emitResponse('[response body unavailable]');}return r;},");
        js.append("function(e){emit({type:'fetch',method:m,url:u,status:0,error:String(e),requestHeaders:rh,requestBody:rb,ms:Date.now()-start});throw e;});};}");
        js.append("var p=w.XMLHttpRequest&&w.XMLHttpRequest.prototype;if(p&&p.open&&p.send){var oldOpen=p.open;var oldSend=p.send;var oldSet=p.setRequestHeader;");
        js.append("p.open=function(m,u){this.__chromitextMethod=m;this.__chromitextUrl=u;this.__chromitextHeaders={};return oldOpen.apply(this,arguments);};");
        js.append("if(oldSet)p.setRequestHeader=function(k,v){try{this.__chromitextHeaders=this.__chromitextHeaders||{};this.__chromitextHeaders[k]=v;}catch(e){}return oldSet.apply(this,arguments);};");
        js.append("p.send=function(body){var x=this;x.__chromitextStart=Date.now();x.__chromitextBody=clip(body,4000);");
        js.append("if(!x.__chromitextBound){x.__chromitextBound=true;x.addEventListener('loadend',function(){var h={};try{var raw=x.getAllResponseHeaders().trim();");
        js.append("if(raw){raw.split(/\\r?\\n/).forEach(function(line){var n=line.indexOf(':');if(n>0)h[line.slice(0,n).trim()]=line.slice(n+1).trim();});}}catch(e){}");
        js.append("var bodyText='';try{bodyText=clip(x.responseText,5000);}catch(e){bodyText='[response body unavailable]';}");
        js.append("emit({type:'xhr',method:x.__chromitextMethod||'GET',url:x.responseURL||x.__chromitextUrl||'',status:x.status,statusText:x.statusText,requestHeaders:x.__chromitextHeaders||{},requestBody:x.__chromitextBody||'',headers:h,responseBody:bodyText,ms:Date.now()-(x.__chromitextStart||Date.now())});});}");
        js.append("return oldSend.apply(this,arguments);};}");
        js.append("})();");
        return js.toString();
    }
}
