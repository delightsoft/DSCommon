module = angular.module 'docflow.ui.utils', []

module.provider '$docflowUtils', [(->

  utils = @
  @$get = (-> return utils)

  # extracts docType from docId (i.e. 'Order@12' or just 'Order')
  utils.docType = (docId) ->
    d = docId.indexOf('@')
    return if d > 0 then docId.substr(0, d) else docId

  # extracts docType from docId (i.e. 'Order@12' or just 'Order')
  utils.splitFullId = ((fullId) ->
    d = fullId.indexOf('@')
    if d > 0
      return {
        docType: fullId.substr(0, d)
        id: fullId.substr(d + 1)}
    return {
      docType: fullId})

  # extracts docType from docId (i.e. 'Order@12' or just 'Order')
  utils.isNewDoc = (docId) ->
    d = docId.indexOf('@')
    return d == -1

  utils.processDocumentsListBeforeEditing = processDocumentsListBeforeEditing = ((list) ->
    if list
      for item in list
        processDocumentBeforeEditing item
    return)

  utils.processDocumentBeforeEditing = processDocumentBeforeEditing = ((item) ->
    return unless item?._u
    for k, v of item._u
      if angular.isArray v
        processDocumentsListBeforeEditing v
        item[k] = v
      else if angular.isObject v
        processDocumentBeforeEditing v
        item[k] = v
    return)

  utils.buildDocumentsListUpdate = buildDocumentsListUpdate = ((list) ->
    return (buildDocumentUpdate(i) for i in list))

  utils.buildDocumentUpdate = buildDocumentUpdate = ((item) ->
    if not item?._u
      return if item.id then item.id else {}
    for k, v of item
      if k != '_u'
        if item._u.hasOwnProperty(k)
          if angular.isArray v
            item._u[k] = buildDocumentsListUpdate v
          else if angular.isObject v
            item._u[k] = buildDocumentUpdate v
          else if angular.isObject item._u[k]
            item._u[k] = null
    item._u.id = item.id if item.id
    item._u.rev = item.rev if item.rev
    return item._u)

    utils.encodeUriQuery = ((val, pctEncodeSpaces) ->
      return `encodeURIComponent(val).
        replace(/%40/gi, '@').
        replace(/%3A/gi, ':').
        replace(/%24/g, '$').
        replace(/%2C/gi, ',').
        replace(/%20/g, (pctEncodeSpaces ? '%20': '+'))`
      )

  return)]