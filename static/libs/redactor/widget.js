if (typeof RedactorPlugins === 'undefined') var RedactorPlugins = {};

RedactorPlugins.advanced = {

    init: function()
    {

        dropdown = {
            widgetAdd: {
                title: 'добавить класс widget',
                callback: function(){
                    $(this.getBlocks()).wrapAll("<div class='widget'/>");
                    this.sync();
                }
            },
            widgetAddFirts: {
                 title: 'добавить класс widget first',
                 callback: function(){
                    $(this.getBlocks()).wrapAll("<div class='widget first'/>");
                    this.sync();
                 }
            },
            widgetAddLast: {
                title: 'добавить класс widget last',
                callback: function(){
                    $(this.getBlocks()).wrapAll("<div class='widget last'/>");
                    this.sync();
                }
            },
            widgetRemove: {
                title: 'удалить класс widget',
                callback: function(){
                    var nodes =this.getBlocks();
                    $(nodes).removeClass("widget").removeClass("last").removeClass("first");
                    this.removeEmptyAttr(nodes, 'class');
                    this.sync();
                }
            }
        }


        this.buttonAddAfter('link','widget','widget', null, dropdown);
        this.buttonAddSeparatorBefore('widget');
    }

}


