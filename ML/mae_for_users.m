% To show that "context reduces the # of mistakes", we plot the Error 
% histograms for BLR, Static Policies, and ZeroR_t
% To show that "context improve accuracy across users", we plot the MAE 
% obtained by the baselines and SVM versus those obtained by our BLR model.
% Each point corresponds to the MAE of a participant.
% Written by Emtiyaz Khan and Joana Soares Machado

clear all; close all;
printOut = 0; % to print the output

% parameters of the experiment
nSeeds = 50;
split_ratio = 0.5; % percentage of users in the learning set
nTestPoints = 20; % number of test decisions for users in test set
start_time = 0.4; % where we start sampling test points
featureIdx = 1:5; % all the features in the dummy data file
nValuesFeatures = [10,3,2,6,2]; % number of possible values that each feature can take, e.g. in_foreground can take 2 values (0 or 1)
appIdx = [1 3 4 7 9]; % indexes of the apps to be considered (e.g., the most popular apps)

errS = []; err0 = []; errl = []; errsvm = [];
mae = []; icr = [];

for s = 1:nSeeds
    
    setSeed(s);
    
    % get data and static policy baseline
    [X, y, XTr, yTr, XTe, yTe, maeStatic, icrStatic, yHatStatic] = get_data(featureIdx, nValuesFeatures, appIdx, split_ratio, nTestPoints, start_time);
    hyp = minimize(0, @fit, -100, 'GP-Lin', y, X, 1, [], []);
    
    % consider only the users with static policies defined
    users = find(sum(~isnan(yHatStatic)));
    
    
    % compute error for each user
    for i = 1:length(users)
        n = users(i);
        % take all observed data
        idx = find(~isnan(yTr(:,n)));
        t = length(idx);
        
        % predict
        % Static policy
        [mae(s,1,i), icr(s,1,i)] = loss(yHatStatic(:,n), yTe(:,n));
        idx = find(~isnan(yTe(:,n)));
        errS = [errS; yHatStatic(idx,n) - yTe(idx,n)];
        
        % Zero R
        yHat = fit([], 'ZeroR', yTr(1:t,n), XTr(1:t,:,n), 1, yTe(:,n), XTe(:,:,n));
        [mae(s,2,i), icr(s,2,i)] = loss(yHat, yTe(:,n));
        err0 = [err0; yHat(idx) - yTe(idx,n)];
        
        % GP Lin
        yHat = fit(hyp, 'GP-Lin', yTr(1:t,n), XTr(1:t,:,n), 1, yTe(:,n), XTe(:,:,n));
        [mae(s,3,i), icr(s,3,i)] = loss(yHat, yTe(:,n));
        errl = [errl; yHat(idx) - yTe(idx,n)];
        
        % SVM
        yHat = fit([], 'SVM', yTr(1:t,n), XTr(1:t,:,n), 1, yTe(:,n), XTe(:,:,n));
        [mae(s,4,i), icr(s,4,i)] = loss(yHat, yTe(:,n));
        errsvm = [errsvm; yHat(idx) - yTe(idx,n)];
    end
end

% histogram plot of error
% calculate total test points to plot the proportion of decisions in the histogram
total_test_points = nSeeds*length(users)*nTestPoints;

figure(1)
subplot(121);
pos = [0:2];
[v, i] = hist(abs(errS(:)), pos);
hl(1,:) = bar(pos, v/total_test_points, 0.8, 'facecolor', 0.7*[1 1 1]);
hold on
[v, i] = hist(abs(err0(:)), pos);
hl(2,:) = bar(pos, v/total_test_points, .6, 'facecolor', [1 0 0]);
hold on
[v, i] = hist(abs(errl(:)), pos);
hl(3,:) = bar(pos, v/total_test_points, .4, 'facecolor', [0.5 0.5 1]);
hold on
[v, i] = hist(abs(errsvm(:)), pos);
hl(4,:) = bar(pos, v/total_test_points, .2, 'facecolor', [0 0.85 0.3]);
legend(hl, 'Static Policy', 'Zero-R_t', 'BLR', 'SVM');

grid on;
hx = xlabel('Mean Absolute Error (MAE)');
hy = ylabel('Number');
ht = title('Context decreases # of mistakes');
set(gca,'FontName','AvantGarde','FontWeight','normal','FontSize',15);
set(ht,'FontName','AvantGarde','FontSize',15,'FontWeight','bold');
set([hx, hy],'FontName','AvantGarde','FontSize',15,'FontWeight','normal');
set(gca, ...
    'Box'         , 'on'     , ...
    'TickDir'     , 'out'     , ...
    'TickLength'  , [.02 .02] , ...
    'XMinorTick'  , 'off'      , ...
    'YMinorTick'  , 'off'      , ...
    'LineWidth'   , 1         );
set(gca, 'xtick', [0:1:2], 'ytick', [0:0.1:1]);
xlim([-.5 2.5]);
ylim([0 1]);
if printOut
    print -dpdf 'ML/output/histogram.pdf';
end

% plot of error across users
figure(2)
plot([-.1:.01:2],[-.1:.01:2],'--k', 'color', 0.7*[1 1 1], 'linewidth', 3)
hold on

% look at the errors per user for one run, e.g. for seed = 2
mae_seed = squeeze(mae(2,:,:));
a = (rand(1,size(users,2)) -0.5)/50; % random noise
hl1(1) = plot(mae_seed(3,:), mae_seed(2,:), 'o', 'color', [0,0,1], 'linewidth', 2, 'markersize', 20, 'markerfacecolor', [1 1 1]);
for i = 1:length(users)
    line([mae_seed(3,i) mae_seed(3,i)]+a(i), [mae_seed(1,i) mae_seed(2,i)], 'color', 0.7*[1 1 1], 'linewidth', 1);
end
hl1(2) = plot(mae_seed(3,:), mae_seed(1,:), 'x', 'color', [1,0,0], 'linewidth', 3, 'markersize', 10, 'markerfacecolor', [1 1 1]);
hl1(3) = plot(mae_seed(3,:), mae_seed(4,:), '*', 'color', [0 0.85 0.3], 'linewidth', 3, 'markersize', 10, 'markerfacecolor', [1 1 1]);

axis([-.02 2.02 -.02 2.02]);
grid on;

hl1 = legend(hl1, 'ZeroR vs BLR', 'Static Policy vs BLR', 'SVM vs BLR', 'location', 'southeast');
hx = xlabel('MAE of Bayesian Linear Regression');
hy = ylabel('MAE of ZeroR, Static Policy, an SVM');
ht = title('Context improves performace across users');
set(gca,'FontName','AvantGarde','FontWeight','normal','FontSize',15);
set(ht,'FontName','AvantGarde','FontSize',15,'FontWeight','bold');
set([hx, hy],'FontName','AvantGarde','FontSize',15,'FontWeight','normal');
set(gca, ...
    'Box'         , 'on'     , ...
    'TickDir'     , 'out'     , ...
    'TickLength'  , [.02 .02] , ...
    'XMinorTick'  , 'off'      , ...
    'YMinorTick'  , 'off'      , ...
    'LineWidth'   , 1         );
set(gca, 'xtick', [0:0.2:2], 'ytick', [0:0.2:2]);

if printOut
    print -dpdf 'ML/output/mae_for_users.pdf'
end


