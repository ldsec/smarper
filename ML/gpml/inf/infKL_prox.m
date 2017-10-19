function [post nlZ dnlZ] = infKL_prox(hyp, mean, cov, lik, x, y)

if hyp.is_cached==1
	global cache_post;
	global cache_nlz;
	global cache_idx;
	
	post=cache_post(cache_idx);
	nlZ=cache_nlz(cache_idx);
	if nargout>2
		warning('to be implemented\n');
		dnlZ = NaN;
	end
	return 
end

snu2=hyp.snu2;
% GP prior
n = size(x,1);
K = feval(cov{:}, hyp.cov, x);                  % evaluate the covariance matrix
m = feval(mean{:}, hyp.mean, x);                      % evaluate the mean vector
K=snu2*eye(n)+K;
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%init value
post_m=hyp.init_m;%k=0
tW = zeros(n,1);%k=-1
post_v=diag(hyp.init_V);%k=0
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
lik_name = func2str(lik{1});
kmax = hyp.max_pass;                                      % maximum number of iterations
rate=hyp.learning_rate;

% iterate
for k = 1:kmax                                                         % iterate
  % step size
  beta = rate;
  r = 1/(beta+1);
  
  if hyp.stochastic_approx==1
	[ll, df, dv] = sampling_E(y, post_m, post_v, lik, hyp.sample_size, hyp.lik);
  else
	switch lik_name
	case {'laplace','likLaplace','poisson','bernoulli_logit','likLogistic'}
		[ll, df, dv] = E_log_p(lik_name, y, post_m, post_v, hyp.lik);
	otherwise
		[ll,df,d2f,dv] = likKL(post_v, lik, hyp.lik, y, post_m);
	end
  end
  W = -2*dv;

  % pseudo observation
  pseudo_y = m + K*df - post_m;

  %remove the following if-else statement if we approximate r^{k} .* tW.^{k-1} by tW.{k}
  if isfield(hyp,'exact')
	  if k==1
		  post_m=post_m+(1-r).*pseudo_y;%m^{1}
	  else
		  sW = sqrt(r.*tW);%r^k .* tW^{k-1}
		  L = chol(eye(n)+sW*sW'.*K); %L = chol(sW*K*sW + eye(n)); 
		  post_m = post_m + (1-r).*(pseudo_y - K*(sW.*( L\ (L'\(sW.*pseudo_y)))));%m^{k+1}
	  end
  end

  tW = r.*tW + (1-r).*W;%tW^{k}
  sW = sqrt(abs(tW)) .* sign(tW);
  L = chol(eye(n)+sW*sW'.*K); %L = chol(sW*K*sW + eye(n)); 

  %use this following line if we approximate r^{k} .* tW.^{k-1} by tW.{k}
  if ~isfield(hyp,'exact')
	  post_m = post_m + (1-r).*(pseudo_y - K*(sW.*( L\ (L'\(sW.*pseudo_y)))));%m^{k+1}
  end

  T = L'\(repmat(sW,1,n).*K); %T  = L'\(sW*K);
  post_v = diag(K) - sum(T.*T,1)'; % v = diag(inv(inv(K)+diag(W))); %v^{k+1}

  alpha = K\(post_m-m);
  nlZ_iter=batch_nlz_fullv2(lik, hyp, sW, K, m, alpha, post_m, y);
  fprintf('pass:%d) %.4f\n', k, nlZ_iter);

  if hyp.is_save==1
	global cache_post;
	global cache_nlz;

	alpha=K\(post_m-m);
	post.sW = sW;                                             % return argument
	post.alpha = alpha;
	post.L = L;                                              % L'*L=B=eye(n)+sW*K*sW

	cache_post=[cache_post; post];
	cache_nlz=[cache_nlz; nlZ_iter];
  end


  if isfield(hyp,'save_iter') && hyp.save_iter==1
	global cache_nlz_iter
	global cache_iter

	cache_nlz_iter=[cache_nlz_iter; nlZ_iter];
	cache_iter=[cache_iter; k];
  end


end

alpha=K\(post_m-m);
post.sW = sW;                                             % return argument
post.alpha = alpha;
post.L = L;                                              % L'*L=B=eye(n)+sW*K*sW

nlZ=batch_nlz_fullv2(lik, hyp, sW, K, m, alpha, post_m, y);
fprintf('final: %.4f\n', nlZ);

if nargout>2
  warning('to be implemented\n');
  dnlZ = NaN;
end
